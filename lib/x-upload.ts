import { Buffer } from 'buffer';
import { getInfoAsync, readAsStringAsync } from 'expo-file-system';
import { xApi } from './x-api';

// X Media Upload is v1.1
const MEDIA_UPLOAD_URL = 'https://upload.twitter.com/1.1/media/upload.json';

export const uploadMedia = async (uri: string): Promise<string | null> => {
    try {
        const fileInfo = await getInfoAsync(uri);
        if (!fileInfo.exists) return null;

        const totalBytes = fileInfo.size;

        // 1. INIT
        const initParams = new URLSearchParams({
            command: 'INIT',
            total_bytes: totalBytes.toString(),
            media_type: 'video/mp4',
            media_category: 'tweet_video',
        });

        const initRes = await xApi.post(MEDIA_UPLOAD_URL, null, {
            params: initParams,
        });

        const mediaId = initRes.data.media_id_string;
        console.log('Media INIT success, media_id:', mediaId);

        // 2. APPEND (Chunked)
        // Read file in chunks (e.g., 1MB)
        const CHUNK_SIZE = 1 * 1024 * 1024;
        let offset = 0;
        let segmentIndex = 0;

        const fileContent = await readAsStringAsync(uri, {
            encoding: 'base64',
        });
        const buffer = Buffer.from(fileContent, 'base64');

        while (offset < totalBytes) {
            const chunk = buffer.slice(offset, offset + CHUNK_SIZE);

            const formData = new FormData();
            formData.append('command', 'APPEND');
            formData.append('media_id', mediaId);
            formData.append('segment_index', segmentIndex.toString());
            formData.append('media_data', chunk.toString('base64'));

            await xApi.post(MEDIA_UPLOAD_URL, formData, {
                headers: {
                    'Content-Type': 'multipart/form-data',
                },
            });

            console.log(`Uploaded segment ${ segmentIndex } `);

            offset += CHUNK_SIZE;
            segmentIndex++;
        }

        // 3. FINALIZE
        const finalizeParams = new URLSearchParams({
            command: 'FINALIZE',
            media_id: mediaId,
        });

        const finalizeRes = await xApi.post(MEDIA_UPLOAD_URL, null, {
            params: finalizeParams,
        });

        console.log('Media FINALIZE success', finalizeRes.data);

        // Check processing info
        if (finalizeRes.data.processing_info) {
            await checkStatus(mediaId);
        }

        return mediaId;
    } catch (error) {
        console.error('Media upload failed', error);
        return null;
    }
};

const checkStatus = async (mediaId: string) => {
    let state = 'in_progress';
    while (state === 'in_progress' || state === 'queued') {
        await new Promise(resolve => setTimeout(resolve, 2000));

        const statusRes = await xApi.get(MEDIA_UPLOAD_URL, {
            params: {
                command: 'STATUS',
                media_id: mediaId,
            },
        });

        state = statusRes.data.processing_info.state;
        console.log('Media processing status:', state);

        if (state === 'failed') {
            throw new Error('Media processing failed');
        }
    }
};
