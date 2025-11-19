import { getToken } from '@/lib/auth';
import { processAndShareRecording } from '@/lib/share';
import { generateVideo } from '@/lib/video-generator';
import { fetchUserProfile, UserProfile } from '@/lib/x-api';
import { Video } from 'expo-av';
import { useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Text, TouchableOpacity, View } from 'react-native';

export default function PreviewScreen() {
  const { uri, duration } = useLocalSearchParams<{ uri?: string | string[]; duration?: string | string[] }>();
  const router = useRouter();

  const [audioUri, setAudioUri] = useState<string | null>(null);
  const [durationMs, setDurationMs] = useState<number | null>(null);
  const [user, setUser] = useState<UserProfile | null>(null);
  const [videoUri, setVideoUri] = useState<string | null>(null);
  const [loadingPreview, setLoadingPreview] = useState(true);
  const [sharing, setSharing] = useState(false);
  const [statusMessage, setStatusMessage] = useState('');

  useFocusEffect(
    useCallback(() => {
      let mounted = true;
      const ensureAuth = async () => {
        const token = await getToken();
        if (!token && mounted) {
          router.replace('/auth');
        }
      };
      ensureAuth();
      return () => {
        mounted = false;
      };
    }, [router])
  );

  useEffect(() => {
    const rawUri = Array.isArray(uri) ? uri[0] : uri;
    const rawDuration = Array.isArray(duration) ? duration[0] : duration;

    if (!rawUri || !rawDuration) {
      router.replace('/(tabs)');
      return;
    }
    try {
      setAudioUri(decodeURIComponent(rawUri));
    } catch {
      setAudioUri(rawUri);
    }
    const parsed = Number(rawDuration);
    if (Number.isFinite(parsed)) {
      setDurationMs(parsed);
    } else {
      router.replace('/(tabs)');
    }
  }, [duration, uri, router]);

  useEffect(() => {
    let mounted = true;
    const loadUser = async () => {
      try {
        const profile = await fetchUserProfile();
        if (mounted) {
          setUser(profile);
        }
      } catch (error) {
        console.error('Failed to fetch profile', error);
        if (mounted) {
          router.replace('/auth');
        }
      }
    };
    loadUser();
    return () => {
      mounted = false;
    };
  }, [router]);

  useEffect(() => {
    if (!audioUri || !user) return;

    let cancelled = false;
    const renderPreview = async () => {
      setLoadingPreview(true);
      setStatusMessage('Generating preview video...');
      const video = await generatePreview(audioUri, user);
      if (!cancelled) {
        setVideoUri(video);
        setLoadingPreview(false);
        if (!video) {
          setStatusMessage('Unable to render preview. You can still share.');
        } else {
          setStatusMessage('');
        }
      }
    };

    renderPreview();

    return () => {
      cancelled = true;
    };
  }, [audioUri, user]);

  const handleShare = async () => {
    if (!audioUri || !durationMs) return;

    setSharing(true);
    try {
      await processAndShareRecording({
        audioUri,
        durationMs,
        user,
        onStatus: setStatusMessage,
        previewVideoUri: videoUri,
      });
      router.replace('/(tabs)');
    } catch (error) {
      console.error('Failed to share', error);
      setStatusMessage('Failed to share. Please try again.');
    } finally {
      setSharing(false);
    }
  };

  const handleRecordAgain = () => {
    router.replace('/(tabs)');
  };

  const durationSeconds = useMemo(() => {
    if (!durationMs) return null;
    return Math.round(durationMs / 1000);
  }, [durationMs]);

  return (
    <View className="flex-1 bg-black px-4 pt-16">
      <Text className="text-white text-2xl font-semibold mb-6">Preview</Text>

      {durationSeconds !== null && (
        <Text className="text-x-gray mb-4">{durationSeconds}s total</Text>
      )}

      <View className="flex-1 w-full items-center justify-center mb-6">
        {loadingPreview ? (
          <ActivityIndicator size="large" color="#1DA1F2" />
        ) : videoUri ? (
          <Video
            source={{ uri: videoUri }}
            useNativeControls
            resizeMode="contain"
            style={{ width: '100%', height: 300, borderRadius: 16, backgroundColor: '#111' }}
            isLooping
          />
        ) : (
          <Text className="text-x-gray">Preview unavailable</Text>
        )}
      </View>

      {statusMessage ? <Text className="text-white mb-4">{statusMessage}</Text> : null}

      <View className="w-full">
        <TouchableOpacity
          className={`bg-white py-4 rounded-full mb-4 ${sharing ? 'opacity-50' : ''}`}
          disabled={sharing}
          onPress={handleShare}
        >
          <Text className="text-center text-black font-bold text-lg">
            {sharing ? 'Sharing...' : 'Share to X'}
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          className="border border-white py-4 rounded-full"
          onPress={handleRecordAgain}
          disabled={sharing}
        >
          <Text className="text-center text-white font-bold text-lg">Record Again</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

async function generatePreview(audioUri: string, user: UserProfile) {
  try {
    const imageUri = user.profile_image_url.replace('_normal', '') || '';
    const video = await generateVideo(audioUri, imageUri);
    return video;
  } catch (error) {
    console.error('Preview generation failed', error);
    return null;
  }
}

