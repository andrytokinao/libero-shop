; Inno Setup script for Libero Shop.
;
; Wraps the jpackage app-image (launcher + embedded Java runtime + obfuscated
; jar) into a real Setup.exe -- WITHOUT WiX, .NET 3.5 or admin rights.
;
; Build the app-image first, then compile this script:
;   mvn -Pinstaller -Dinstaller.os=portable verify
;   iscc backend\src\main\innosetup\liberty-shop.iss
; Produces: backend\target\jpackage\dist\LiberoShop-Setup-1.0.0.exe
;
; Paths can be overridden:  iscc /DAppVersion=1.2.0 /DSourceDir=... liberty-shop.iss

#define AppName "Libero Shop"
#ifndef AppVersion
  #define AppVersion "1.0.0"
#endif
#define Publisher "Kinga Digital"
#define ExeName "Libero Shop.exe"
; app-image folder produced by jpackage (relative to this .iss file).
#ifndef SourceDir
  #define SourceDir "..\..\..\target\jpackage\dist\Libero Shop"
#endif
#ifndef OutDir
  #define OutDir "..\..\..\target\jpackage\dist"
#endif

[Setup]
; A stable, unique AppId keeps upgrades/uninstalls tied to the same product.
AppId={{8F3B2A14-6C7D-4E59-9A2B-0F1E2D3C4B5A}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#Publisher}
; Per-user install: no UAC prompt, lands in a writable location.
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
OutputDir={#OutDir}
OutputBaseFilename=LiberoShop-Setup-{#AppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
; app-image is 64-bit only.
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible

[Languages]
Name: "french"; MessagesFile: "compiler:Languages\French.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#ExeName}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#ExeName}"; Tasks: desktopicon
Name: "{group}\{cm:UninstallProgram,{#AppName}}"; Filename: "{uninstallexe}"

[Run]
Filename: "{app}\{#ExeName}"; Description: "{cm:LaunchProgram,{#AppName}}"; Flags: nowait postinstall skipifsilent
