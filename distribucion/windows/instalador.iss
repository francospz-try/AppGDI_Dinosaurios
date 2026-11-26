#ifndef Version
  #define Version "0.0.0"
#endif
[Setup]
AppId=AppGDI-Dinosaurios
AppName=AppGDI Dinosaurios
AppVersion={#Version}
DefaultDirName={localappdata}\Programs\AppGDI-Dinosaurios
DefaultGroupName=AppGDI Dinosaurios
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
OutputDir=salida
OutputBaseFilename=AppGDI-Dinosaurios-Windows-x64
Compression=none
WizardStyle=modern

[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"

[Files]
Source: "paquete\*"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\AppGDI Dinosaurios"; Filename: "{app}\iniciar.cmd"

[Run]
Filename: "{app}\iniciar.cmd"; Description: "Abrir AppGDI Dinosaurios"; Flags: postinstall shellexec skipifsilent
