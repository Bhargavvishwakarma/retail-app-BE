package com.retail.notification.dto;

/** A compact mutation/count response that avoids exposing internal persistence details. */
public record NotificationCountResponse(long count) {
}
