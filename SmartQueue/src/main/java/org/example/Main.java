package org.example;

import com.smartqueue.service.ApiServer;
import com.smartqueue.service.ReminderService;
import com.smartqueue.service.SyncService;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        System.out.println("Starting SmartQueue...");
        new SyncService().syncAll();
        new ApiServer().start();

        // Check for appointments needing a reminder every 30 minutes
        ReminderService reminderService = new ReminderService();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        // An uncaught exception would silently cancel every later run, so catch it here.
        scheduler.scheduleAtFixedRate(() -> {
            try {
                reminderService.sendReminders();
            } catch (Exception e) {
                System.out.println("Reminder check failed (will retry in 30 minutes): " + e.getMessage());
            }
        }, 0, 30, TimeUnit.MINUTES);

        System.out.println("Open index.html in your browser to use the system.");
    }
}