package org.ggzzll.p2pminecraft.Bukkit;

import dev.onvoid.webrtc.*;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.zip.Deflater;

public final class p2pminecraft extends JavaPlugin implements Listener {

    private PeerConnectionFactory Factory;
    private RTCPeerConnection PeerConnection;
    private RTCDataChannel DataChannel;
    public ArrayList<String> NetWork = new ArrayList<>();
    public ArrayList<Socket> Minecraft = new ArrayList<>();

    @Override
    public void onEnable() {
        getLogger().info(getDescription().getName() + " 已启用！");

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        saveDefaultConfig();

        Factory = new PeerConnectionFactory();

        RTCIceServer iceServer = new RTCIceServer();
        iceServer.urls.add("stun:stun.l.google.com:19302");

        RTCConfiguration config = new RTCConfiguration();
        config.iceServers.add(iceServer);
        config.iceTransportPolicy = RTCIceTransportPolicy.ALL;

        PeerConnection = Factory.createPeerConnection(config, new PeerConnectionObserver() {
            @Override
            public void onIceCandidate(RTCIceCandidate candidate) {
                String[] Sdp = candidate.sdp.split(" ");
                NetWork.add(Sdp[5]);
                // public IP
                NetWork.add(Sdp[6]);
                // public Port
                if (Sdp.length <= 16 && !Sdp[7].equals("srflx")) return;
                getLogger().info("请使用以下IP和端口进行连接 " + Sdp[4] + ":" + Sdp[5]);
            }

            @Override
            public void onConnectionChange(RTCPeerConnectionState State) {
                if (State == RTCPeerConnectionState.CONNECTED) {
                    try {
                        Socket Socket = new Socket((!org.bukkit.Bukkit.getIp().isEmpty() ? org.bukkit.Bukkit.getIp() : "localhost"), org.bukkit.Bukkit.getPort());
                        Socket.setKeepAlive(true);
                        Minecraft.add(State.ordinal(), Socket);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (State == RTCPeerConnectionState.DISCONNECTED) {
                    try {
                        Minecraft.get(State.ordinal()).close();
                        Minecraft.remove(State.ordinal());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            @Override
            public void onDataChannel(RTCDataChannel DataChannel) {
                if (!DataChannel.getProtocol().equals("Minecraft")) return;
                DataChannel.registerObserver(new RTCDataChannelObserver() {
                    @Override
                    public void onBufferedAmountChange(long PreviousAmount) { }

                    @Override
                    public void onStateChange() { }

                    @Override
                    public void onMessage(RTCDataChannelBuffer Buffer) {
                        Socket Socket = Minecraft.get(DataChannel.getState().ordinal());
                        try {
                            OutputStream OutputStream = Socket.getOutputStream();
                            OutputStream.write(Buffer.data.get());
                            OutputStream.close();
                            InputStream InputStream = Socket.getInputStream();
                            byte[] Data = InputStream.readAllBytes();
                            InputStream.close();
                            DataChannel.send(new RTCDataChannelBuffer(ByteBuffer.wrap(Data), false));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        // 将数据传输给TCP然后返回TCP返回内容
                    }
                });
            }
        });

        DataChannel = PeerConnection.createDataChannel("minecraft", new RTCDataChannelInit());

        PeerConnection.createOffer(new RTCOfferOptions(), new CreateSessionDescriptionObserver() {
            @Override
            public void onSuccess(RTCSessionDescription description) {
                PeerConnection.setLocalDescription(description, new SetSessionDescriptionObserver() {
                    @Override
                    public void onSuccess() {

                        final byte[] bytes = new byte[256];
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(256);
                        Deflater Zip = new Deflater(Deflater.BEST_COMPRESSION);

                        Zip.setInput(description.toString().getBytes(StandardCharsets.UTF_8));
                        Zip.finish();

                        while (!Zip.finished()) {
                            int length = Zip.deflate(bytes);
                            outputStream.write(bytes, 0, length);
                        }

                        Zip.end();

                        String Base64Description = Base64.getEncoder().encodeToString(outputStream.toByteArray());

                        getLogger().info(getDescription().getName() + " 设置Offer成功，本地描述" + Base64Description);
                        getLogger().info("接下来请使用以下IP端口进行连接，如果没有提示您的IP和端口连接地址信息，请检查您的网络有可能无法使用p2p");
                    }

                    @Override
                    public void onFailure(String error) {
                        getLogger().info(getDescription().getName() + " 设置Offer失败，原因" + error);
                    }
                });
            }

            @Override
            public void onFailure(String error) {
                getLogger().info(getDescription().getName() + " 设置Offer失败，原因" + error);
            }
        });

        getLogger().info(getDescription().getName() + " 初始化完成！");
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        for (Socket socket : Minecraft) {
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        DataChannel.close();
        PeerConnection.close();
        Factory.dispose();
    }
}
