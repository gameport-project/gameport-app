package com.unity3d.player;

/**
 * A stand-in for Unity's player class, put in an Unreal game that carries Meta's plugins: they read the current activity from this class and die on a null when there is
 * none (see UnrealUnityPlayerStubPatch). The hook of GamePort fills the field. Never added to a game that has a Unity player of its own.
 */
public final class UnityPlayer {
    public static android.app.Activity currentActivity;

    private UnityPlayer() { }
}
