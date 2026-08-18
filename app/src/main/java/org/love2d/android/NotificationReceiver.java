/**
 * Copyright (c) 2006-2024 LOVE Development Team
 *
 * This software is provided 'as-is', without any express or implied
 * warranty.  In no event will the authors be held liable for any damages
 * arising from the use of this software.
 */
package org.love2d.android;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import androidx.core.app.NotificationCompat;

public class NotificationReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "love_game_notifications";

    // 👇 Color de RESPALDO: si en algún teléfono quedara un anillo blanco,
    //    se pintará de este color. Pon aquí el color dominante de tu
    //    splash_background.png en formato HEX.
    private static final String ICON_BACKGROUND_COLOR = "#2196F3";

    @Override
    public void onReceive(Context context, Intent intent) {
        int id = intent.getIntExtra("notification_id", 0);
        String title = intent.getStringExtra("title");
        String message = intent.getStringExtra("message");

        if (title == null) title = "Notificación";
        if (message == null) message = "";

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Notificaciones del juego",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notificaciones de plantas y eventos del juego");
            nm.createNotificationChannel(channel);
        }

        // Create intent to open the game when notification is tapped
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        PendingIntent pending = PendingIntent.getActivity(
            context, id, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Build and show notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            // 👇👇 EL CAMBIO CLAVE:
            //    Usa el MISMO ícono adaptativo que el launcher de tu app
            //    (el que se crea con splash_background.png y llena todo el espacio)
            .setSmallIcon(R.mipmap.ic_launcher)
            // Color de respaldo por si algún teléfono deja un anillo blanco
            .setColor(Color.parseColor(ICON_BACKGROUND_COLOR))
            .setColorized(true)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        nm.notify(id, builder.build());
    }
}
