package com.minjeong.assistant

import android.service.notification.NotificationListenerService

/**
 * 다른 앱(Spotify, YouTube 등)의 미디어 재생 정보를 감지하기 위한 서비스.
 * 실제 로직은 없고, MainActivity의 MediaSessionManager가 이 서비스를 통해
 * 시스템으로부터 미디어 세션 정보를 받아올 수 있도록 "등록부" 역할만 한다.
 */
class MediaNotificationListener : NotificationListenerService()