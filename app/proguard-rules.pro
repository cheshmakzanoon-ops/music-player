# Local library models use explicit JSON parsing and need no reflective keep rules.
# Spotify's published AAR supplies consumer rules for its Gson protocol Item models.
# SDK 0.8.0 also bundles an unused optional Jackson mapper and a compile-time
# annotation. ConnectionParams defaults to GsonMapper; these absent optional
# types do not participate in this app's playback connection.
-dontwarn com.fasterxml.jackson.databind.deser.std.StdDeserializer
-dontwarn com.fasterxml.jackson.databind.ser.std.StdSerializer
-dontwarn com.spotify.base.annotations.NotNull
