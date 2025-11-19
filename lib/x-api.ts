import axios from 'axios';
import { getToken } from './auth';

const BASE_URL = 'https://api.twitter.com/2';

export const xApi = axios.create({
    baseURL: BASE_URL,
});

xApi.interceptors.request.use(async (config) => {
    const token = await getToken();
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

export interface UserProfile {
    id: string;
    name: string;
    username: string;
    profile_image_url: string;
    verified: boolean;
    verified_type?: string; // 'blue', 'business', 'government', 'none'
}

export const fetchUserProfile = async (): Promise<UserProfile> => {
    const response = await xApi.get('/users/me', {
        params: {
            'user.fields': 'profile_image_url,verified,verified_type',
        },
    });
    return response.data.data;
};

export const postTweet = async (text: string, mediaIds: string[] = [], replyToId?: string) => {
    const body: any = {
        text: text,
    };

    if (mediaIds.length > 0) {
        body.media = {
            media_ids: mediaIds,
        };
    }

    if (replyToId) {
        body.reply = {
            in_reply_to_tweet_id: replyToId,
        };
    }

    const response = await xApi.post('/tweets', body);
    return response.data.data;
};
