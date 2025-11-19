import { generateVideo } from './video-generator';
import { uploadMedia } from './x-upload';
import { postTweet, UserProfile } from './x-api';

type StatusSetter = (message: string) => void;

interface ShareParams {
    audioUri: string;
    durationMs: number;
    user: UserProfile | null;
    onStatus?: StatusSetter;
    previewVideoUri?: string | null;
}

const PREMIUM_DURATION_LIMIT = 7200; // seconds (2 hours)
const STANDARD_DURATION_LIMIT = 140; // seconds
const CHUNK_DURATION = 140;

export const processAndShareRecording = async ({
    audioUri,
    durationMs,
    user,
    onStatus,
    previewVideoUri,
}: ShareParams) => {
    if (!audioUri) {
        throw new Error('Missing audio URI');
    }

    const durationSec = durationMs / 1000;
    const limit = isPremium(user) ? PREMIUM_DURATION_LIMIT : STANDARD_DURATION_LIMIT;
    const profileImage = user?.profile_image_url?.replace('_normal', '') || '';

    const videoUris: string[] = [];

    if (durationSec <= limit) {
        if (previewVideoUri) {
            videoUris.push(previewVideoUri);
        } else {
            onStatus?.('Generating video...');
            const videoUri = await generateVideo(audioUri, profileImage);
            if (videoUri) {
                videoUris.push(videoUri);
            }
        }
    } else {
        const numParts = Math.ceil(durationSec / CHUNK_DURATION);
        for (let i = 0; i < numParts; i++) {
            const start = i * CHUNK_DURATION;
            const chunkDuration = Math.min(CHUNK_DURATION, durationSec - start);
            onStatus?.(`Processing part ${i + 1}/${numParts}...`);
            const videoUri = await generateVideo(audioUri, profileImage, start, chunkDuration);
            if (videoUri) {
                videoUris.push(videoUri);
            }
        }
    }

    if (videoUris.length === 0) {
        throw new Error('Video generation failed');
    }

    const mediaIds: string[] = [];
    for (let i = 0; i < videoUris.length; i++) {
        onStatus?.(`Uploading part ${i + 1}/${videoUris.length}...`);
        const mediaId = await uploadMedia(videoUris[i]);
        if (mediaId) {
            mediaIds.push(mediaId);
        }
    }

    if (mediaIds.length === 0) {
        throw new Error('Upload failed');
    }

    onStatus?.('Posting to X...');
    let replyToId: string | undefined;
    for (let i = 0; i < mediaIds.length; i++) {
        const text = mediaIds.length > 1 ? `Audio Note Part ${i + 1}/${mediaIds.length}` : 'Audio Note via EchoX';
        const tweet = await postTweet(text, [mediaIds[i]], replyToId);
        replyToId = tweet.id;
    }

    onStatus?.('Shared successfully!');
};

const isPremium = (user: UserProfile | null) =>
    user?.verified_type === 'blue' || user?.verified_type === 'business';





