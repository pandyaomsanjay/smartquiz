package com.smartquiz

import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.EmailAuthProvider

object AuthHelper {

    /**
     * Section 10: Reusable Authentication Provider Detection
     * Reliably checks the Firebase User's provider data, avoiding local flags.
     */
    fun hasPasswordProvider(user: FirebaseUser?): Boolean {
        return user?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } == true
    }

    fun hasGoogleProvider(user: FirebaseUser?): Boolean {
        return user?.providerData?.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } == true
    }

    /**
     * Section 12: Centralized Error Handling
     * Maps Firebase exceptions to user-friendly messages without exposing backend details.
     */
    fun getErrorMessage(exception: Exception?): String {
        if (exception == null) return "An unknown error occurred."

        return when (exception) {
            is FirebaseAuthWeakPasswordException -> "Choose a stronger password (at least 6 characters)."
            is FirebaseAuthInvalidCredentialsException -> {
                when {
                    exception.message?.contains("password", ignoreCase = true) == true -> "The current password is incorrect."
                    exception.message?.contains("email", ignoreCase = true) == true -> "Please enter a valid email address."
                    else -> "Invalid credentials. Please try again."
                }
            }
            is FirebaseAuthInvalidUserException -> "No account found with this email."
            is FirebaseAuthRecentLoginRequiredException -> "Your session is too old. Please log out and sign in again to change your password."
            is FirebaseAuthUserCollisionException -> "This email is already associated with another account."
            is FirebaseAuthException -> {
                when (exception.errorCode) {
                    "ERROR_NETWORK_REQUEST_FAILED" -> "Unable to connect. Please check your internet and try again."
                    "ERROR_USER_DISABLED" -> "This account has been disabled."
                    "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Please try again later."
                    else -> "Authentication failed. Please try again."
                }
            }
            else -> {
                val msg = exception.message ?: ""
                if (msg.contains("network", ignoreCase = true)) {
                    "Unable to connect. Please try again."
                } else {
                    "An error occurred: ${exception.localizedMessage}"
                }
            }
        }
    }
}