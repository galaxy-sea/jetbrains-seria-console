

 # Serial-Console

[https://plugins.jetbrains.com/plugin/32966-serial-console](https://plugins.jetbrains.com/plugin/32966-serial-console)

<p><b>Serial Console</b> is a serial port debugging tool for JetBrains IDEs. It works with USB serial adapters and common serial links such as RS-232 and RS-485, and is suitable for Modbus RTU frame debugging. It provides serial port discovery, custom port paths, editor-based serial sessions, send and receive panels, HEX/text encoding, CRC calculation including Modbus CRC, line ending handling, flow control, signal status, logs, and per-port settings persistence.</p>
<p><b>Serial Console</b> 是一款面向 JetBrains IDE 的串口调试工具，适用于 USB 串口以及 RS-232、RS-485 等常见串口连接，并支持 Modbus RTU 报文调试。插件提供串口发现、自定义串口路径、基于 Editor 的串口会话、发送与接收面板、HEX/文本编码、包含 Modbus CRC 在内的 CRC 计算、行结束符、流控制、信号状态、日志输出以及按串口持久化设置。</p>


## Virtual Serial Port

[socat install](http://www.dest-unreach.org/socat/)


> tty

```bash
# nohup \
socat -d -d \
  PTY,raw,echo=0,link=/tmp/ttyVirtualA \
  PTY,raw,echo=0,link=/tmp/ttyVirtualB \
# > /tmp/socat-tty.log 2>&1 &
```

### TCP Server
> TCP 

```bash
# nohup \
socat -d -d \
  PTY,raw,echo=0,link=/tmp/ttyTcpServer \
  TCP-LISTEN:9000,reuseaddr \
# > /tmp/socat-tcp-server.log 2>&1 &
```
 
> TCP Client

```bash
# nohup \
socat -d -d \
  PTY,raw,echo=0,link=/tmp/ttyTcpClient \
  TCP:127.0.0.1:9000 \
# > /tmp/socat-tcp-client.log 2>&1 &
```

### UDP

>  UDP Server

```bash
# nohup \
socat -d -d \
  PTY,raw,echo=0,link=/tmp/ttyUdpServer \
  UDP-LISTEN:9001,reuseaddr \
# > /tmp/socat-udp-server.log 2>&1 &
```

>  UDP Client

```bash
# nohup \
socat -d -d \
  PTY,raw,echo=0,link=/tmp/ttyUdpClient \
  UDP:127.0.0.1:9001 \
# > /tmp/socat-udp-client.log 2>&1 &
```
