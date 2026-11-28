#ifndef Version
  #define Version "0.0.0"
#endif
#ifndef Variante
  #define Variante "Headless"
#endif
; Los accesos directos abren javaw.exe sin ventana de consola. Los .cmd quedan
; para usarlos a mano desde la carpeta instalada.
#define Clase "sermed.comprobantes.distribucion.InicioNativo"
#define Java "{app}\java\bin\javaw.exe"
#define Ruta '-cp ""{app}\inicio.jar;{app}\app.jar"" ' + Clase + ' ""{app}""'

[Setup]
AppId=AppGDI-Dinosaurios-{#Variante}
AppName=AppGDI Dinosaurios ({#Variante})
AppVersion={#Version}
AppPublisher=Grupo Dinosaurios - UCSM
DefaultDirName={localappdata}\Programs\AppGDI-Dinosaurios-{#Variante}
DefaultGroupName=AppGDI Dinosaurios ({#Variante})
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
; Espacio para la base que se crea en %LOCALAPPDATA% en la primera ejecucion
; (unos 70 MB, con margen para el registro de transacciones).
ExtraDiskSpaceRequired=314572800
OutputDir=salida
OutputBaseFilename=AppGDI-Dinosaurios-{#Variante}-Windows-x64
Compression=lzma2/fast
SolidCompression=yes
WizardStyle=modern
UninstallDisplayIcon={#Java}

[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"

[Files]
Source: "paquete\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\AppGDI Dinosaurios ({#Variante})"; Filename: "{#Java}"; Parameters: "{#Ruta}"; WorkingDir: "{app}"
Name: "{autodesktop}\AppGDI Dinosaurios ({#Variante})"; Filename: "{#Java}"; Parameters: "{#Ruta}"; WorkingDir: "{app}"; Tasks: escritorio
Name: "{group}\Detener PostgreSQL de AppGDI"; Filename: "{#Java}"; Parameters: "{#Ruta} --detener"; WorkingDir: "{app}"
Name: "{group}\Respaldar la base de AppGDI"; Filename: "{#Java}"; Parameters: "{#Ruta} --respaldar"; WorkingDir: "{app}"
Name: "{group}\Restaurar un respaldo de AppGDI"; Filename: "{#Java}"; Parameters: "{#Ruta} --restaurar"; WorkingDir: "{app}"
Name: "{group}\Desinstalar AppGDI Dinosaurios ({#Variante})"; Filename: "{uninstallexe}"

[Tasks]
Name: "escritorio"; Description: "Crear un acceso directo en el escritorio"

[Run]
Filename: "{#Java}"; Parameters: "{#Ruta}"; WorkingDir: "{app}"; Description: "Abrir AppGDI Dinosaurios"; Flags: postinstall nowait skipifsilent

[UninstallRun]
Filename: "{#Java}"; Parameters: "{#Ruta} --detener"; Flags: runhidden waituntilterminated; RunOnceId: "DetenerPostgreSQL"

[Code]
// Antes de reinstalar o actualizar, detiene el servidor de una instalacion
// anterior para que sus archivos no esten en uso.
function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  Codigo: Integer;
  App, Java: String;
begin
  Result := '';
  App := ExpandConstant('{app}');
  Java := App + '\java\bin\javaw.exe';
  if FileExists(Java) then
    Exec(Java, '-cp "' + App + '\inicio.jar;' + App + '\app.jar" {#Clase} "' + App + '" --detener',
      App, SW_HIDE, ewWaitUntilTerminated, Codigo);
end;
