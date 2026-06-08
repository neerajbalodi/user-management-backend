package com.syn.usermanagement.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Centralized service for all custom business metrics.
 *
 * Metrics exposed at /actuator/prometheus and scraped by Prometheus:
 *
 *  Counters (ever-increasing):
 *    - users_registered_total          : successful user registrations
 *    - users_registration_failed_total : failed registration attempts
 *    - users_login_success_total       : successful logins
 *    - users_login_failed_total        : failed login attempts
 *    - users_logout_total              : successful logouts
 *    - users_created_total             : users created via CRUD API
 *    - users_updated_total             : users updated
 *    - users_deleted_total             : users deleted
 *    - users_photo_uploads_total       : successful photo uploads
 *    - users_photo_upload_errors_total : failed photo uploads
 *
 *  Gauges (current snapshot):
 *    - users_active_total              : current count of users in DB
 *
 *  Timers:
 *    - users_login_duration_seconds    : time spent processing login
 */
@Slf4j
@Service
public class UserMetricsService {

    // -------------------------------------------------------
    // Counters — Auth
    // -------------------------------------------------------
    private final Counter registrationSuccessCounter;
    private final Counter registrationFailedCounter;
    private final Counter loginSuccessCounter;
    private final Counter loginFailedCounter;
    private final Counter logoutCounter;

    // -------------------------------------------------------
    // Counters — CRUD
    // -------------------------------------------------------
    private final Counter userCreatedCounter;
    private final Counter userUpdatedCounter;
    private final Counter userDeletedCounter;

    // -------------------------------------------------------
    // Counters — Photo
    // -------------------------------------------------------
    private final Counter photoUploadSuccessCounter;
    private final Counter photoUploadErrorCounter;

    // -------------------------------------------------------
    // Gauge — Active users
    // -------------------------------------------------------
    private final AtomicLong activeUsersCount = new AtomicLong(0);

    // -------------------------------------------------------
    // Timer — Login duration
    // -------------------------------------------------------
    private final Timer loginTimer;

    // -------------------------------------------------------
    // Constructor — register all meters
    // -------------------------------------------------------
    public UserMetricsService(MeterRegistry registry) {

        // Auth counters
        registrationSuccessCounter = Counter.builder("users_registered_total")
                .description("Total number of successful user registrations")
                .tag("result", "success")
                .register(registry);

        registrationFailedCounter = Counter.builder("users_registration_failed_total")
                .description("Total number of failed user registration attempts")
                .tag("result", "failure")
                .register(registry);

        loginSuccessCounter = Counter.builder("users_login_total")
                .description("Total number of user login events")
                .tag("result", "success")
                .register(registry);

        loginFailedCounter = Counter.builder("users_login_total")
                .description("Total number of user login events")
                .tag("result", "failure")
                .register(registry);

        logoutCounter = Counter.builder("users_logout_total")
                .description("Total number of successful user logouts")
                .register(registry);

        // CRUD counters
        userCreatedCounter = Counter.builder("users_operations_total")
                .description("Total number of user CRUD operations")
                .tag("operation", "create")
                .register(registry);

        userUpdatedCounter = Counter.builder("users_operations_total")
                .description("Total number of user CRUD operations")
                .tag("operation", "update")
                .register(registry);

        userDeletedCounter = Counter.builder("users_operations_total")
                .description("Total number of user CRUD operations")
                .tag("operation", "delete")
                .register(registry);

        // Photo counters
        photoUploadSuccessCounter = Counter.builder("users_photo_uploads_total")
                .description("Total number of user photo upload events")
                .tag("result", "success")
                .register(registry);

        photoUploadErrorCounter = Counter.builder("users_photo_uploads_total")
                .description("Total number of user photo upload events")
                .tag("result", "error")
                .register(registry);

        // Gauge — backed by AtomicLong so it reflects live DB count set externally
        Gauge.builder("users_active_total", activeUsersCount, AtomicLong::get)
                .description("Current total number of users in the system")
                .register(registry);

        // Timer for login processing time
        loginTimer = Timer.builder("users_login_duration_seconds")
                .description("Time taken to process a login request")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        log.info("UserMetricsService initialized — all custom metrics registered.");
    }

    // -------------------------------------------------------
    // Public API — Auth
    // -------------------------------------------------------

    /** Call when a user successfully registers. */
    public void incrementRegistrationSuccess() {
        registrationSuccessCounter.increment();
    }

    /** Call when a registration attempt fails (e.g. duplicate email). */
    public void incrementRegistrationFailed() {
        registrationFailedCounter.increment();
    }

    /** Call after a successful login. */
    public void incrementLoginSuccess() {
        loginSuccessCounter.increment();
    }

    /** Call after a failed login (bad credentials, etc.). */
    public void incrementLoginFailed() {
        loginFailedCounter.increment();
    }

    /** Call after a successful logout. */
    public void incrementLogout() {
        logoutCounter.increment();
    }

    /** Record the time taken for a login operation. */
    public Timer.Sample startLoginTimer() {
        return Timer.start();
    }

    public void stopLoginTimer(Timer.Sample sample) {
        sample.stop(loginTimer);
    }

    // -------------------------------------------------------
    // Public API — CRUD
    // -------------------------------------------------------

    /** Call after a user is successfully created via the CRUD API. */
    public void incrementUserCreated() {
        userCreatedCounter.increment();
    }

    /** Call after a user is successfully updated. */
    public void incrementUserUpdated() {
        userUpdatedCounter.increment();
    }

    /** Call after a user is successfully deleted. */
    public void incrementUserDeleted() {
        userDeletedCounter.increment();
    }

    // -------------------------------------------------------
    // Public API — Photo
    // -------------------------------------------------------

    /** Call after a photo upload succeeds. */
    public void incrementPhotoUploadSuccess() {
        photoUploadSuccessCounter.increment();
    }

    /** Call after a photo upload fails. */
    public void incrementPhotoUploadError() {
        photoUploadErrorCounter.increment();
    }

    // -------------------------------------------------------
    // Public API — Gauge
    // -------------------------------------------------------

    /**
     * Set the current active user count.
     * Call this on startup or whenever the count changes significantly.
     */
    public void setActiveUsersCount(long count) {
        activeUsersCount.set(count);
    }

    /** Increment the active user count by 1 (after a user is created). */
    public void incrementActiveUsers() {
        activeUsersCount.incrementAndGet();
    }

    /** Decrement the active user count by 1 (after a user is deleted). */
    public void decrementActiveUsers() {
        activeUsersCount.decrementAndGet();
    }
}
