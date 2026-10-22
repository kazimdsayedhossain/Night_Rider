package com.kazisayed.deliverit3d;

/** UI/sensor threads publish scalar input; the game owns all simulation state. */
public final class PlayerInput {
    public volatile boolean pedal, brake, left, right, tilt = true;
    public volatile float touchSteer;

    public void release() {
        pedal = brake = left = right = false;
        touchSteer = 0;
    }
}
