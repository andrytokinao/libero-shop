package com.houssen.liberoshop.entity;

/**
 * An order asked to do something its current status forbids: hand over goods already handed
 * over, cancel an order already paid, remit cash nobody collected.
 *
 * <p>Thrown by the domain itself, so it depends on no outer layer; the web layer turns it into a
 * 409 like any refused business rule. The codes are the ones the API already answered before the
 * rules moved here, so the screens keep reacting the same way; the messages are for a cashier.
 */
public class StatusTransitionException extends RuntimeException {

    private final String code;

    private StatusTransitionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }

    static StatusTransitionException of(String orderNumber, PaymentTransition refused, PaymentStatus current) {
        String order = "La commande " + orderNumber;
        String cannotCancel = refused == PaymentTransition.CANCEL ? " : elle ne peut plus etre annulee." : ".";
        return switch (current) {
            case CANCELLED -> new StatusTransitionException("ALREADY_CANCELLED", order + " est deja annulee.");
            case COLLECTED, REMITTED, PAID -> new StatusTransitionException("ALREADY_SETTLED",
                    order + " est deja reglee" + cannotCancel);
            case UNPAID -> new StatusTransitionException("NOT_COLLECTED",
                    order + " n'a pas ete encaissee : aucun argent a verser.");
        };
    }

    static StatusTransitionException of(String orderNumber, DeliveryTransition refused, DeliveryStatus current) {
        String order = "La commande " + orderNumber;
        return switch (current) {
            case DELIVERED -> new StatusTransitionException("ALREADY_DELIVERED", order + " a deja ete remise"
                    + (refused == DeliveryTransition.CANCEL ? " : elle ne peut plus etre annulee." : "."));
            case CANCELLED -> new StatusTransitionException("CANCELLED", order + " a ete annulee : rien a remettre.");
            case IN_PROGRESS -> new StatusTransitionException("ALREADY_HANDLED", order + " est deja prise en charge.");
            case PENDING -> new StatusTransitionException("NOT_HANDLED", order + " n'est prise en charge par personne.");
        };
    }

    /** Someone else is preparing the order: the step is theirs to take. */
    static StatusTransitionException handledByOther(String orderNumber, String handlerName) {
        return new StatusTransitionException("HANDLED_BY_OTHER",
                "La commande " + orderNumber + " est en cours de service par " + handlerName + ".");
    }
}
