package io.github.musicplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class AudioUrlPolicyTest {
    @Test fun permitsSecureAudioUrlsWithSignedQueryParameters() {
        assertEquals("https://audio.example/song.ogg?expires=123&signature=abc", AudioUrlPolicy.validateHttps(" https://audio.example/song.ogg?expires=123&signature=abc "))
    }

    @Test fun rejectsCleartextLocalSchemesEmbeddedCredentialsAndFragments() {
        listOf("http://audio.example/song.mp3", "file:///private/audio.mp3", "content://audio/1", "spotify:track:abc", "https://user:secret@audio.example/song.mp3", "https://audio.example/song.mp3#part", "not a url").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { AudioUrlPolicy.validateHttps(value) }
        }
    }

    @Test fun acceptsSupportedAudioContainers() {
        listOf(
            "RIFFxxxxWAVEfmt ", "OggSabcdefghijkl", "fLaCabcdefghijkl", "ID3abcdefghijklmn", "xxxxftypM4A abcde",
        ).forEach { assertTrue(it, AudioUrlPolicy.looksLikeAudio(it.toByteArray())) }
        assertTrue(AudioUrlPolicy.looksLikeAudio(byteArrayOf(0xff.toByte(), 0xfb.toByte()) + ByteArray(14)))
    }

    @Test fun rejectsHtmlErrorsPlaylistsAndTruncatedData() {
        listOf("<!DOCTYPE html><html>", "#EXTM3U\nhttps://example/audio", "OggS").forEach {
            assertFalse(it, AudioUrlPolicy.looksLikeAudio(it.toByteArray()))
        }
    }
}
