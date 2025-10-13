# TaskExpense Manager

Aplicación Android para gestión de tareas y gastos con sincronización en tiempo real.

## 🔥 Configuración de Firebase

1. Ve a https://console.firebase.google.com/
2. Crea un nuevo proyecto llamado "TaskExpenseManager"
3. Agrega una app Android con el package: `com.example.taskexpensemanager`
4. Descarga el archivo `google-services.json`
5. Coloca `google-services.json` en la carpeta `app/`

## 🗄️ Configurar Realtime Database

1. En Firebase Console, ve a "Realtime Database"
2. Click "Crear base de datos"
3. Modo: Comenzar en modo de prueba
4. Ve a "Reglas" y pega:

```json
{
  "rules": {
    "lists": {
      "$listId": {
        ".read": true,
        ".write": true
      }
    }
  }
}
```

## 📱 Abrir el proyecto

1. Abre Android Studio
2. File → Open
3. Selecciona la carpeta `TaskExpenseManager`
4. Espera a que Gradle sincronice
5. Coloca el archivo `google-services.json` en `app/`
6. Run!

## ✨ Características

- ✅ Gestión de tareas tipo Todoist
- 💰 Control de gastos por tarea
- 📊 Total de gastos mensuales
- 🔄 Sincronización en tiempo real
- 👥 Compartir listas con otros usuarios
- 🏷️ Categorías personalizables
- 📅 Fechas de vencimiento
- 🎯 Prioridades con colores

## 🆓 Gratis para siempre

Firebase Plan Spark (gratuito):
- 1 GB almacenamiento
- 10 GB/mes descarga
- 100 conexiones simultáneas
