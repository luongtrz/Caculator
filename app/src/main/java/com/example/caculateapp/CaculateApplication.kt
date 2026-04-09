package com.example.caculateapp

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/**
 * Application class for app-wide initialization
 */
class CaculateApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Enable Firestore offline persistence (new API)
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()
        
        Firebase.firestore.firestoreSettings = settings
    }
}
