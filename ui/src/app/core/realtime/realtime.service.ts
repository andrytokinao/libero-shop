import { Injectable, effect, inject, signal, untracked } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { Observable, Subject, filter } from 'rxjs';
import { ServerConfig } from '../config/server-config.service';
import { AppNotification, NotificationType } from '../models';
import { AuthService } from '../services/auth.service';
import { TokenStorage } from '../services/token-storage.service';

/** Where the server's STOMP endpoint lives — WebSocketConfiguration.ENDPOINT. */
const ENDPOINT = '/ws';
/** The account's own queue; the broker resolves `/user` against the connection. */
const USER_QUEUE = '/user/queue/notifications';
/** Matches the server's heartbeat, so a dead connection is noticed within the minute. */
const HEARTBEAT_MS = 20_000;
/** Pause between two connection attempts while the server is unreachable. */
const RECONNECT_DELAY_MS = 5_000;

export type ConnectionState = 'offline' | 'connecting' | 'online';

/** A topic some part of the app listens to, and the STOMP subscription serving it. */
interface TopicEntry {
  readonly subject: Subject<unknown>;
  listeners: number;
  subscription?: StompSubscription;
}

/**
 * The single WebSocket of the application, and the only code that knows it is STOMP.
 *
 * <p>Everything that arrives in real time comes through here and leaves as an Observable:
 * `notifications$` for the messages addressed to the signed-in account, `on(type)` for one
 * kind of them, `topic(name)` for what the server publishes to everyone. A screen subscribes
 * with `takeUntilDestroyed()` like to any other stream and never sees a frame, a reconnection
 * or a token.
 *
 * <p>The connection follows the session on its own: opened when someone signs in, closed when
 * they sign out, re-opened with backoff when the server restarts or the network drops. Each
 * attempt reads the token afresh, so a new sign-in is picked up without a page reload. A refusal
 * of the token itself stops the attempts rather than repeating them every few seconds — the
 * next REST call's 401 signs the user out, and signing back in reconnects.
 *
 * <p>What is pushed is a hint, never the data of record: a screen that hears of a new sale
 * reloads from the REST API. A notification missed while offline is therefore harmless — the
 * next reload shows the truth.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private readonly auth = inject(AuthService);
  private readonly tokens = inject(TokenStorage);
  private readonly server = inject(ServerConfig);

  private readonly incoming = new Subject<AppNotification>();
  private readonly topics = new Map<string, TopicEntry>();
  private readonly connection = signal<ConnectionState>('offline');

  /** For a status dot; screens react to `notifications$`, not to this. */
  readonly state = this.connection.asReadonly();

  /** Every notification addressed to the signed-in account, as it arrives. */
  readonly notifications$: Observable<AppNotification> = this.incoming.asObservable();

  private readonly client = new Client({
    reconnectDelay: RECONNECT_DELAY_MS,
    heartbeatIncoming: HEARTBEAT_MS,
    heartbeatOutgoing: HEARTBEAT_MS,
    beforeConnect: (client) => {
      const token = this.tokens.token();
      if (token === null) {
        // Signed out between two attempts: nothing to connect as.
        void client.deactivate();
        return;
      }
      this.connection.set('connecting');
      // Read at each attempt, like the token: the mobile app may have been pointed at another
      // server since the last one.
      client.brokerURL = this.server.socketUrl(ENDPOINT);
      client.connectHeaders = { Authorization: `Bearer ${token}` };
    },
    onConnect: () => {
      this.connection.set('online');
      this.client.subscribe(USER_QUEUE, (message) => this.dispatch(message));
      // Subscriptions do not survive a reconnection; the topics still wanted are renewed.
      this.topics.forEach((entry, name) => this.subscribeTopic(name, entry));
    },
    onStompError: (frame) => {
      // The server refused the CONNECT: token expired, forged or account disabled.
      console.warn('Notifications en temps réel refusées :', frame.headers['message']);
      void this.client.deactivate();
    },
    onWebSocketClose: () => {
      this.topics.forEach((entry) => (entry.subscription = undefined));
      this.connection.set(this.client.active ? 'connecting' : 'offline');
    },
  });

  constructor() {
    effect(() => {
      const signedIn = this.auth.isAuthenticated() && this.tokens.token() !== null;
      // The client's callbacks write to `connection`; kept out of the effect's tracking.
      untracked(() => (signedIn ? this.start() : this.stop()));
    });
  }

  /** The notifications of one kind, with their `data` typed for it. */
  on<T>(type: NotificationType): Observable<AppNotification<T>> {
    return this.notifications$.pipe(
      filter((notification): notification is AppNotification<T> => notification.type === type),
    );
  }

  /**
   * What the server publishes on `/topic/<name>` — NotificationService.publish. Subscribed on
   * the socket while at least one caller listens, and renewed after every reconnection.
   */
  topic<T>(name: string): Observable<T> {
    return new Observable<T>((subscriber) => {
      let entry = this.topics.get(name);
      if (!entry) {
        entry = { subject: new Subject<unknown>(), listeners: 0 };
        this.topics.set(name, entry);
      }
      const current = entry;
      current.listeners++;
      if (this.client.connected && !current.subscription) {
        this.subscribeTopic(name, current);
      }
      const inner = current.subject.subscribe((value) => subscriber.next(value as T));

      return () => {
        inner.unsubscribe();
        if (--current.listeners === 0) {
          current.subscription?.unsubscribe();
          this.topics.delete(name);
        }
      };
    });
  }

  private start(): void {
    if (!this.client.active) {
      this.client.activate();
    }
  }

  private stop(): void {
    if (this.client.active) {
      void this.client.deactivate();
    }
    this.connection.set('offline');
  }

  private subscribeTopic(name: string, entry: TopicEntry): void {
    entry.subscription = this.client.subscribe(`/topic/${name}`, (message) => {
      const body = parse(message);
      if (body !== undefined) {
        entry.subject.next(body);
      }
    });
  }

  private dispatch(message: IMessage): void {
    const notification = parse(message) as AppNotification | undefined;
    if (notification && typeof notification.type === 'string') {
      this.incoming.next(notification);
    }
  }
}

/** A frame body as JSON, or undefined — one malformed message must not end the stream. */
function parse(message: IMessage): unknown {
  try {
    return JSON.parse(message.body);
  } catch {
    console.warn('Message temps réel illisible, ignoré.');
    return undefined;
  }
}
