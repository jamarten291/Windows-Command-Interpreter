# Intérprete de comandos multiplataforma (Windows y Linux)

Este es un intérprete de comandos de Windows que usa Java.

>[!NOTE]
> Este intérprete de comandos funciona tanto en Windows como en Linux

# Comandos
| Comando                                                                 | Descripción                                                                           |
|------------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| `ejecuta <comando> [parametros] IN null\|fichero OUT null\|fichero ERR null\|fichero.ext TIMEOUT ms` | Ejecuta un proceso con redirección de entrada/salida/error y timeout en milisegundos. |
| `run <cmd> [args...] [--timeout=seg]`                                  | Ejecuta un proceso en primer plano con E/S heredadas. Timeout opcional en segundos.   |
| `runbg <cmd> [args...]`                                                | Lanza un proceso en background. Redirige salida/error a logs y registra el proceso.   |
| `jobs`                                                                 | Lista los procesos en background con su estado, PID, comando y tiempos.               |
| `kill <pid>`                                                           | Intenta terminar un proceso por su PID si está registrado.                            |
| `details <pid>`                                                        | Muestra información detallada del proceso: comando, usuario, estado, tiempos, etc.    |
| `getenv`                                                               | Muestra todas las variables de entorno actuales del proceso.                          |
| `getDirectory`                                                         | Muestra el directorio de trabajo actual del intérprete.                               |
| `timeout [seg]`                                                        | Muestra o establece el timeout por defecto para ejecuciones.                          |
| `history`                                                              | Muestra la ruta del historial de ejecuciones y permite consultar entradas anteriores. |
| `exit`                                                                 | Finaliza el intérprete de comandos.                                                   |