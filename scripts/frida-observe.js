'use strict';

// Loaded by the frida-tools CLI, which bundles frida-java-bridge in Frida 17.
// Observe our own activity only. No authentication, network or media hooks.
if (typeof Java === 'undefined' || !Java.available) {
    throw new Error('Android Java bridge is unavailable in this target');
}

Java.perform(function () {
    const Activity = Java.use('android.app.Activity');
    const onResume = Activity.onResume.overload();
    let observed = false;

    onResume.implementation = function () {
        const result = onResume.call(this);
        if (!observed && this.getClass().getName().toString() === 'io.github.musicplayer.MainActivity') {
            observed = true;
            console.log('MUSIC_PLAYER_FRIDA_MAIN_ACTIVITY_RESUMED');
        }
        return result;
    };
    console.log('MUSIC_PLAYER_FRIDA_OBSERVER_READY');
});
