import 'dart:async';
import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:web_socket_channel/web_socket_channel.dart';
import 'package:web_socket_channel/status.dart' as status;

/// Credentials model for Xtream Codes
class XtreamCredentials {
  final String serverUrl;
  final String username;
  final String password;

  const XtreamCredentials({
    required this.serverUrl,
    required this.username,
    required this.password,
  });

  factory XtreamCredentials.fromJson(Map<String, dynamic> json) {
    return XtreamCredentials(
      serverUrl: json['serverUrl'] as String? ?? '',
      username: json['username'] as String? ?? '',
      password: json['password'] as String? ?? '',
    );
  }
}

/// Provisioning payload received from the Admin Web Portal
class ProvisionPayload {
  final String playlistType; // "m3u" or "xtream"
  final String? playlistUrl;
  final String? epgUrl;
  final XtreamCredentials? xtreamData;

  const ProvisionPayload({
    required this.playlistType,
    this.playlistUrl,
    this.epgUrl,
    this.xtreamData,
  });

  factory ProvisionPayload.fromJson(Map<String, dynamic> json) {
    return ProvisionPayload(
      playlistType: json['playlistType'] as String? ?? 'm3u',
      playlistUrl: json['playlistUrl'] as String?,
      epgUrl: json['epgUrl'] as String?,
      xtreamData: json['xtreamData'] != null
          ? XtreamCredentials.fromJson(json['xtreamData'] as Map<String, dynamic>)
          : null,
    );
  }
}

/// Active session metadata
class PairingSession {
  final String code;
  final int expiresInSeconds;
  final String qrUrl;
  final String webSocketUrl;

  const PairingSession({
    required this.code,
    required this.expiresInSeconds,
    required this.qrUrl,
    required this.webSocketUrl,
  });
}

/// Production-ready Remote Pairing Service for Flutter IPTV apps
class RemotePairingService {
  final http.Client _httpClient;
  WebSocketChannel? _channel;
  StreamSubscription? _subscription;
  bool _isDisposed = false;

  RemotePairingService({http.Client? httpClient})
      : _httpClient = httpClient ?? http.Client();

  /// Initiates the pairing session and connects the real-time WebSocket.
  ///
  /// [serverBaseUrl] should be e.g. "https://pair.opentv.app" or "http://192.168.1.50:3000".
  /// [onSessionReady] is triggered when the 6-character code and QR URL are received.
  /// [onProvisioned] is triggered when the admin submits credentials.
  /// [onError] reports network or validation issues.
  Future<void> startPairing({
    required String serverBaseUrl,
    required void Function(PairingSession session) onSessionReady,
    required void Function(ProvisionPayload payload) onProvisioned,
    required void Function(String error) onError,
    void Function()? onConnected,
  }) async {
    _isDisposed = false;
    final cleanBaseUrl = serverBaseUrl.replaceAll(RegExp(r'/+$'), '');

    // Step 1: Hit POST /api/pair/init
    try {
      final response = await _httpClient.post(
        Uri.parse('$cleanBaseUrl/api/pair/init'),
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json',
        },
        body: jsonEncode({}),
      );

      if (_isDisposed) return;

      if (response.statusCode != 200) {
        onError('Failed to initialize session: HTTP ${response.statusCode}');
        return;
      }

      final data = jsonDecode(response.body) as Map<String, dynamic>;
      final code = data['code'] as String;
      final expiresIn = data['expiresIn'] as int? ?? 600;

      final qrUrl = '$cleanBaseUrl/?code=$code';

      // Format WebSocket URL (ws:// or wss://)
      final isSecure = cleanBaseUrl.toLowerCase().startsWith('https://');
      final host = cleanBaseUrl.replaceFirst(RegExp(r'^https?://'), '');
      final wsUrl = '${isSecure ? 'wss' : 'ws'}://$host/?code=$code';

      final session = PairingSession(
        code: code,
        expiresInSeconds: expiresIn,
        qrUrl: qrUrl,
        webSocketUrl: wsUrl,
      );

      onSessionReady(session);

      // Step 2: Open WebSocket connection
      _connectWebSocket(
        wsUrl: wsUrl,
        onConnected: onConnected,
        onProvisioned: onProvisioned,
        onError: onError,
      );
    } catch (e) {
      if (!_isDisposed) {
        onError('Network error during session initialization: $e');
      }
    }
  }

  void _connectWebSocket({
    required String wsUrl,
    void Function()? onConnected,
    required void Function(ProvisionPayload payload) onProvisioned,
    required void Function(String error) onError,
  }) {
    try {
      final channel = WebSocketChannel.connect(Uri.parse(wsUrl));
      _channel = channel;

      _subscription = channel.stream.listen(
        (dynamic rawMessage) {
          if (_isDisposed) return;
          try {
            final json = jsonDecode(rawMessage.toString()) as Map<String, dynamic>;
            final type = json['type'] as String?;

            if (type == 'connected') {
              onConnected?.call();
            } else if (type == 'provision') {
              final payload = ProvisionPayload.fromJson(json);
              onProvisioned(payload);
              // Cleanly close after receiving provision data
              dispose();
            }
          } catch (e) {
            onError('Error parsing incoming configuration: $e');
          }
        },
        onError: (error) {
          if (!_isDisposed) {
            onError('WebSocket communication error: $error');
          }
        },
        onDone: () {
          // Socket closed by server or network
        },
        cancelOnError: true,
      );
    } catch (e) {
      if (!_isDisposed) {
        onError('Could not connect to WebSocket: $e');
      }
    }
  }

  /// Cancel active subscriptions and close open sockets
  void dispose() {
    _isDisposed = true;
    _subscription?.cancel();
    _subscription = null;
    _channel?.sink.close(status.goingAway);
    _channel = null;
  }
}
