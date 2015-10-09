package com.example.dendimon.vpn_test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

/**
 * Created by Dendimon on 10/9/2015.
 */

//Todo: reduce public mutability
public class Packet {
    public static final int IP4_Header_size = 20;
    public static final int TCP_Header_size = 20;
    public static final int UDP_Header_size = 8;

    public IP4Header ip4Header;
    public TCPHeader tcpHeader;
    public UDPHeader udpHeader;
    public ByteBuffer backingBuffer;

    private boolean isTCP;
    private boolean isUDP;

    public Packet (ByteBuffer buffer) throws UnknownHostException{
        this.ip4Header = new IP4Header(buffer);
        if(this.ip4Header.protocol == IP4Header.TransportProtocol.TCP){
            this.tcpHeader = new TCPHeader(buffer);
            this.isTCP = true;
        }else if (ip4Header.protocol == IP4Header.TransportProtocol.UDP) {
            this.udpHeader = new UDPHeader(buffer);
            this.isUDP=true;
        }
        this.backingBuffer = buffer;
    }

    @Override
    public String toString(){
        final StringBuilder sb = new StringBuilder("Packet(");
        sb.append("ip4Header=").append(ip4Header);
        if(isTCP)sb.append(", tcpHeader=").append(tcpHeader);
        else if(isUDP)sb.append(", udpHeader=").append(udpHeader);
        sb.append(", payloadSize=").append(backingBuffer.limit()-backingBuffer.position());
        sb.append('}');
        return sb.toString();
    }

    public boolean isTCP(){return isTCP;}

    public boolean isUDP(){return isUDP;}

    public void swapSourceAndDestination(){
        InetAddress newSourceAddress = ip4Header.destinationAddress;
        ip4Header.destinationAddress = ip4Header.sourceAddress;
        ip4Header.sourceAddress = newSourceAddress;

        if (isUDP){
            int  newSourcePort = udpHeader.destinationPort;
            udpHeader.destinationPort = udpHeader.sourcePort;
            udpHeader.sourcePort = newSourcePort;
        }else if(isTCP){
            int newSourcePort = tcpHeader.destinationPort;
            tcpHeader.destinationPort = tcpHeader.sourcePort;
            tcpHeader.sourcePort = newSourcePort;
        }
    }

    public void updateTCPBuffer (ByteBuffer buffer, byte flags, long sequenceNum,long ackNum,int payloadSize){
        buffer.position(0);
        fillHeader(buffer);
        backingBuffer = buffer;

        tcpHeader.flags = flags;
        backingBuffer.put(IP4_Header_size+13,flags);

        tcpHeader.sequenceNumber = sequenceNum;
        backingBuffer.putInt(IP4_Header_size + 4, (int) sequenceNum);

        tcpHeader.acknowledgementNumber = ackNum;
        backingBuffer.putInt(IP4_Header_size+8,(int)ackNum);

        //reset header size, since we dont need options
        byte dataOffset = (byte) (TCP_Header_size<<2);
        tcpHeader.dataOffsetAndReserved = dataOffset;
        backingBuffer.put(IP4_Header_size + 12, dataOffset);

        updateTCPChecksum(payloadSize);

        int ip4TotalLength = IP4_Header_size + TCP_Header_size + payloadSize;
        backingBuffer.putShort(2,(short)ip4TotalLength);
        ip4Header.totalLength = ip4TotalLength;

        updateIP4Checksum();
    }

    public void updateUDPBuffer(ByteBuffer buffer, int payloadSize){
        buffer.position(0);
        fillHeader(buffer);
        backingBuffer = buffer;

        int udpTotalLength = UDP_Header_size + payloadSize;
        backingBuffer.putShort(IP4_Header_size+4,(short) udpTotalLength);
        udpHeader.length = udpTotalLength;

        //disable udp checksum validation
        backingBuffer.putShort(IP4_Header_size + 6, (short) 0);
        udpHeader.checksum = 0;

        int ip4TotalLength = IP4_Header_size + udpTotalLength;
        backingBuffer.putShort(2,(short) ip4TotalLength);
        ip4Header.totalLength = ip4TotalLength;

        updateIP4Checksum();
    }

    private void updateIP4Checksum(){
        ByteBuffer buffer = backingBuffer.duplicate();
        buffer.position(0);

        //Clear previous checksum
        buffer.putShort(10,(short) 0);

        int ipLength = ip4Header.headerLength;
        int sum = 0;
        while (ipLength>0){
            sum += BitUtils.getUnsignedShort(buffer.getShort());
            ipLength-=2;
        }
        while (sum>>16>0)sum=(sum & 0xFFFF)+(sum >> 16);
        sum= ~sum;
        ip4Header.headerChecksum = sum;
        backingBuffer.putShort(10, (short) sum);
    }

    private void updateTCPChecksum(int payloadSize){
        int sum = 0;
        int  tcpLength = TCP_Header_size + payloadSize;

        //calculate pseudo-header checksum
        ByteBuffer buffer = ByteBuffer.wrap(ip4Header.sourceAddress.getAddress());
        sum = BitUtils.getUnsignedShort(buffer.getShort()) + BitUtils.getUnsignedShort(buffer.getShort());

        buffer = ByteBuffer.wrap(ip4Header.destinationAddress.getAddress());
        sum += BitUtils.getUnsignedShort(buffer.getShort())+BitUtils.getUnsignedShort(buffer.getShort());

        sum += IP4Header.TransportProtocol.TCP.getNumber() + tcpLength;

        buffer = backingBuffer.duplicate();
        //clear previous checksum
        buffer.position(IP4_HEADER_SIZE);
        while (tcpLength > 1)
        {
            sum += BitUtils.getUnsignedShort(buffer.getShort());
            tcpLength -= 2;
        }
        if (tcpLength > 0)
            sum += BitUtils.getUnsignedByte(buffer.get()) << 8;

        while (sum >> 16 > 0)
            sum = (sum & 0xFFFF) + (sum >> 16);

        sum = ~sum;
        tcpHeader.checksum = sum;
        backingBuffer.putShort(IP4_HEADER_SIZE + 16, (short) sum);
    }

    private void fillHeader(ByteBuffer buffer){
        ip4Header.fillHeader(buffer);
        if(isUDP)
            udpHeader.fillHeader(buffer);
        else if (isTCP)
            tcpHeader.fillHeader(buffer);
    }

    public static class IP4Header{

        public byte version;
        public byte IHL;
        public int headerLength;
        public short typeOfService;
        public int totalLength;

        public int identificationAndFlagsAndFragmentOffset;

        public short TTL;
        private short protocolNum;
        public TransportProtocol protocol;
        public int headerChecksum;

        public InetAddress sourceAddress;
        public InetAddress destinationAddress;

        public int optionAndPadding;

        private enum TransportProtocol{
            TCP(6),
            UDP(17),
            Other(0xFF);

            private int protocolNumber;

            TransportProtocol(int protocolNumber){this.protocolNumber = protocolNumber};

            private static TransportProtocol numberToEnum(int protocolNumber){
                if(protocolNumber == 6)
                    return TCP;
                else if (protocolNumber == 17)
                    return UDP;
                else
                    return Other;
            }

            public int getNumber(){return this.protocolNumber;}
        }

        private IP4Header (ByteBuffer buffer) throws UnknownHostException{
            byte versionAndIHL = buffer.get();
            this.version = (byte) (versionAndIHL >> 4);
            this.IHL = (byte)(versionAndIHL & 0x0F);
            this.headerLength = this.IHL <<2;

            this.typeOfService = BitUtils.getUnsignedByte(buffer.get());



        }

    }





}
