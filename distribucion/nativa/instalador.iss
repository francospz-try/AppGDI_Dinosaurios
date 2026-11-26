#ifndef Version
  #define Version "0.0.0"
#endif
#ifndef Variante
  #define Variante "Headless"
#endif
[Setup]
AppId=AppGDI-Dinosaurios-{#Variante}
AppName=AppGDI Dinosaurios ({#Variante})
AppVersion={#Version}
DefaultDirName={localappdata}\Programs\AppGDI-Dinosaurios-{#Variante}
DefaultGroupName=AppGDI Dinosaurios ({#Variante})
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
OutputDir=salida
OutputBaseFilename=AppGDI-Dinosaurios-{#Variante}-Windows-x64
Compression=lzma2/fast
SolidCompression=yes
WizardStyle=modern

[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"

[Files]
Source: "paquete\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\AppGDI Dinosaurios ({#Variante})"; Filename: "{app}\iniciar.cmd"
#if Variante == "Headless"
Name: "{group}\Detener PostgreSQL de AppGDI"; Filename: "{app}\detener.cmd"
#endif

[Run]
Filename: "{app}\iniciar.cmd"; Description: "Abrir AppGDI Dinosaurios"; Flags: postinstall shellexec skipifsilent

[UninstallRun]
Filename: "{cmd}"; Parameters: "/C ""{app}\detener.cmd"""; Flags: runhidden
