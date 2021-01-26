/**
 * @ClassName Traceroute.java
 * @author zhao
 * @version 1.0.0
 * @Description TODO
 * @createTime 2020年12月25日 22:04:00
 */
package com.internet.traceroutedemo.service;

import com.alibaba.fastjson.JSON;
import com.ejlchina.okhttps.OkHttps;
import com.internet.traceroutedemo.model.IpInformation;
import jpcap.JpcapCaptor;
import jpcap.JpcapSender;
import jpcap.NetworkInterface;
import jpcap.NetworkInterfaceAddress;
import jpcap.packet.EthernetPacket;
import jpcap.packet.ICMPPacket;
import jpcap.packet.IPPacket;
import jpcap.packet.Packet;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
public class Traceroute
{
    private int deviceCount = 0;
    private JpcapCaptor captor = null;
    private NetworkInterface device = null;
    private InetAddress localIP = null;
    private boolean resolveNames = false;
    private String hostName;
    private int initialTTL=0;
    //构造函数，构造时计算一下一共有多少个网卡
    public Traceroute( )
    {
        deviceCount = JpcapCaptor.getDeviceList ().length;
    }
    //获取所有的设备的列表
    public String[] getInterfaceList ()
    {
        this.deviceCount = JpcapCaptor.getDeviceList ().length;
        String[] deviceList = new String[ this.deviceCount ];
        NetworkInterface[]devList= JpcapCaptor.getDeviceList();
        for(int i=0;i<this.deviceCount;i++){
            String description=devList[i].description;
            for(NetworkInterfaceAddress address:devList[i].addresses){
                if(address.address instanceof Inet4Address){
                    description=address.address+"====="+devList[i].description;
                }
            }
        }
        return deviceList;
    }
    //打开接收网络数据包的设备
    public void openDevice( int deviceNo )
    {
        device = JpcapCaptor.getDeviceList()[ deviceNo ];
        localIP = null;
        captor = null;
        try
        {
            captor = JpcapCaptor.openDevice( device, 2000,  false,  1 );
            for( NetworkInterfaceAddress addr : device.addresses )
            {
                if( addr.address instanceof Inet4Address ) {
                    localIP = addr.address;
                    break;
                }
            }
        }
        catch ( IOException e )
        {
            device  = null;
            localIP = null;
            captor  = null;
        }
    }

    public String getStringMAC(byte[] origin){
                StringBuffer stringBuffer=new StringBuffer();
                for (byte b : origin) {
                    stringBuffer.append(Integer.toHexString(b & 0xff) + ":");
                }
                return  stringBuffer.toString();
    }
//获取本地网关的mac地址
    public byte[] obtainDefaultGatewayMac( String httpHostToCheck )
    {
        System.out.println( "正在获取本地网关的MAC地址 ");
        byte[] gatewayMAC = null;
        if ( captor != null ) try
        {
            InetAddress hostAddr = InetAddress.getByName( httpHostToCheck );
            captor.setFilter( "tcp and dst host " + hostAddr.getHostAddress(), true );
            int timeoutTimer = 0;
            new URL("http://" + httpHostToCheck ).openStream().close();

            while( true )
            {
                Packet ping = captor.getPacket ();
                //第一次获取没有获取到数据包这时就开始等待循环20次每次100ms，
                if( ping == null )
                {
                    if ( timeoutTimer < 20 ) {
                        Thread.sleep( 100);
                        ++timeoutTimer;
                        continue;
                    }
                    //等待2S过后还是没有抓取到数据包说明超时了，没法获取到本地网关mac地址
                    System.out.println("获取mac地址超时！");
                    return gatewayMAC;
                }
                //如果获取到了数据包开始解析数据包里面携带的本地网关的mac地址信息
                byte[] destinationMAC = ((EthernetPacket)ping.datalink).dst_mac;
                //本能本地网关就是自己
                if( ! Arrays.equals( destinationMAC, device.mac_address ) ) {
                    gatewayMAC = destinationMAC;
                    break;
                }
                //捕获到一个数据包没有默认网关mac地址，重新开始捕获
                timeoutTimer = 0;
                new URL("http://" + httpHostToCheck ).openStream().close();
            }
        }
        catch( Exception e )
        {
            System.out.println( "ERROR: " + e.toString () );
        }
        System.out.println( " 获取网关MAC地址完成！MAC地址为：");
        print(getStringMAC(gatewayMAC));
        return gatewayMAC;

    }

    //根据传入的ip地址返回一个IpInformation对象
    private IpInformation getIpInformationObject(String ip){
        String ipinformation="";
        try{
        ipinformation=OkHttps.sync("http://ip-api.com/json/"+ip).get().getBody().toString();}
        catch (Exception e){
            e.printStackTrace();
            return null;
        }
        return JSON.parseObject(ipinformation,IpInformation.class);
    }


    //打印带有时间的信息
    public static  void printwithtime(String text){
        SimpleDateFormat formatter= new SimpleDateFormat("HH:mm:ss z");
        Date date = new Date(System.currentTimeMillis());
        System.out.println(text+">>>"+formatter.format(date));
    }
    //直接打印信息
    public static void print(String text){
        System.out.println(text);
    }

    public void trace (int deviceNo, String hostName)
    {
        Boolean flag=Boolean.TRUE;
        openDevice( deviceNo );
        this.hostName   = hostName;
        try
        {
            print( "Looking up " + hostName );
            InetAddress remoteIP = InetAddress.getByName( hostName );
            print( "  " + remoteIP.getHostAddress ());
            print("");
            byte[] defaultGatewayMAC = obtainDefaultGatewayMac( "www.baidu.com" );

            //如果没有找到默认的网关的话直接结束运行
            if ( defaultGatewayMAC == null )
            {
                print("没有找到默认网关地址");
                return;
            }
            print( "Tracing route to " + remoteIP  );
            //创建ICMP包
            ICMPPacket icmp = new ICMPPacket();
            icmp.type       = ICMPPacket.ICMP_ECHO;
            icmp.seq        = 100;
            icmp.id         = 0;
            icmp.data       = "开始traceroute".getBytes ();
            icmp.setIPv4Parameter(0, false, false, false, 0, false, false, false, 0, 0, 0, IPPacket.IPPROTO_ICMP, localIP, remoteIP);
            EthernetPacket ether = new EthernetPacket();
            ether.frametype = EthernetPacket.ETHERTYPE_IP;
            ether.src_mac   = device.mac_address;
            ether.dst_mac   = defaultGatewayMAC;
            icmp.datalink   = ether;
            //开始发送ICMP包
            JpcapSender sender = captor.getJpcapSenderInstance ();
            //设置接收packet的过滤器，只接收目的地址是本机的ICMP包
            captor.setFilter( "icmp and dst host " + localIP.getHostAddress(), true );
            icmp.hop_limit  = (short)initialTTL;
            //计时器，每一个计时器代表100ms
            int timeoutTimer = 0;
            //超时计数器
            int timeoutCounter = 0;
            //设置初始时间，方便计算耗时
            long tStart = System.nanoTime ();
            sender.sendPacket( icmp );
            while( flag)
            {
                //接收返回的ICMP数据包
                ICMPPacket p = (ICMPPacket)captor.getPacket ();
                int tDelay = (int)( ( System.nanoTime () - tStart ) / 1000000L );
                if( p == null )
                {
                    //一开始没有接收到包，每100ms检查一次，最多检查30次看有没有返回的包
                    if ( timeoutTimer < 20 )
                    {
                        Thread.sleep( 100 );
                        ++timeoutTimer;
//                        if ( timeoutTimer >= 10 ) {
////                            System.out.print(".");
//                        }
                        continue;
                    }
                    //超过2s还没有返回数据包说明超时了，
                    ++timeoutCounter;
//                    print( "目前TTL已有超时的次数为：" + timeoutCounter );
                    //如果超时的次数小于3次，那么重发数据
                    if ( timeoutCounter < 3 )
                    {
//                        print( "当前的TTL为: "+icmp.hop_limit);
                        tStart = System.nanoTime ();
                        timeoutTimer = 0;
                        sender.sendPacket( icmp );
                    }
                    //超时次数多余3次就直接增加TTL
                    else
                    {

                        ++icmp.hop_limit;
                        timeoutTimer = 0;
                        timeoutCounter = 0;
                        tStart = System.nanoTime ();
                        sender.sendPacket( icmp );
                    }
                    continue;
                }

                //当获得了一个ICMP数据包时
                String hopID = p.src_ip.getHostAddress ();
                if ( resolveNames ) {
                    p.src_ip.getHostName ();
                    hopID = p.src_ip.toString ();
                }
                //关键代码：判断收到的packet是不是超时，如果是那么就增加TTL
                if( p.type == ICMPPacket.ICMP_TIMXCEED )
                {
                   IpInformation ipInformation=getIpInformationObject(hopID);
                    //如果获取ip信息成功显示相关信息
                    if("success".equals(ipInformation.getStatus())){
                        print( hopID + ", " + tDelay + " ms"+"     路由的地理位置为："+ipInformation.getCountry()+
                                "  "+ipInformation.getRegionName()+"  "+ipInformation.getCity()+"  "+"ISP为： "+ipInformation.getIsp()+
                                "   AS为："+ipInformation.getAs()
                        );

                    }else{
                        if(icmp.hop_limit==0){}else{
                        System.out.print( hopID + ", " + tDelay + " ms" );}
                    }
                    //增加ttl
                    ++icmp.hop_limit;
                    print("");
                    print("==================================================================================================================================================================================================================================================");
                    print( "当前TTL为："+icmp.hop_limit );
                    timeoutTimer = 0;
                    timeoutCounter = 0;
                    tStart = System.nanoTime ();
                    sender.sendPacket( icmp );
                }
                //到达目的主机
                else if( p.type == ICMPPacket.ICMP_ECHOREPLY )
                {

                    IpInformation ipInformation=getIpInformationObject(hopID);
                    //如果获取ip信息成功显示相关信息
                    if("success".equals(ipInformation.getStatus())){
                        print( hopID + ", " + tDelay + " ms"+"     路由的地理位置为："+ipInformation.getCountry()+
                                "  "+ipInformation.getRegionName()+"  "+ipInformation.getCity()+"  "+"ISP为： "+ipInformation.getIsp()+
                                        "   AS为："+ipInformation.getAs()
                                );

                    }else{
                        System.out.print( hopID + ", " + tDelay + " ms" );
                    }

                    //显示对应的ip地址的相关信息
                    flag=Boolean.FALSE;


                }
            }
        }
        catch( Exception e )
        {
            print( "ERROR: " + e.toString () );

            return;
        }
        print(  "Traceroute完成！！");

    }


}
