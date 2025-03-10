package org.cagnulein.qzcompanionnordictracktreadmill;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.DecimalFormatSymbols;
import java.util.Calendar;
import java.util.Locale;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.TextView;

/*
 * Linux command to send UDP:
 * #socat - UDP-DATAGRAM:192.168.1.255:8002,broadcast,sp=8002
 */
public class UDPListenerService extends Service {
    private static final String LOG_TAG = "QZ:UDPListenerService";

    static String UDP_BROADCAST = "UDPBroadcast";

    //Boolean shouldListenForUDPBroadcast = false;
    static DatagramSocket socket;

    static int y1Speed;      //vertical position of slider at 2.0
    static int y1Inclination;    //vertical position of slider at 0.0
    static double lastReqResistance = 0;
    static int y1Resistance;

    static long lastSwipeMs = 0;
    static double reqCachedSpeed = -1;
    static double reqCachedResistance = -1;
    static double reqCachedInclination = -100;

    public enum _device {
        x11i,
        nordictrack_2950,
        other,
        proform_2000,
        s22i,
		tdf10,
		t85s,
        s40,
        exp7i,
        x32i,
        c1750,
        t65s,
        nordictrack_2950_maxspeed22,
        t75s,
        grand_tour_pro,
        proform_studio_bike_pro22,
        x32i_NTL39019,
        x22i,
        NTEX71021,
        c1750_2021,
        s22i_NTEX02121_5,
        x32i_NTL39221,
        c1750_2020,        
        elite1000,
        x14i,
        nordictrack_2450,
    }

    public static _device device;

    private final ShellRuntime shellRuntime = new ShellRuntime();

    public static void setDevice(_device dev) {
        switch(dev) {
            case x11i:
                y1Speed = 600;      //vertical position of slider at 2.0
                y1Inclination = 557;    //vertical position of slider at 0.0
                break;
            case x22i:
                y1Speed = 785;      //vertical position of slider at 2.0
                y1Inclination = 785;    //vertical position of slider at 0.0
                break;                
            case nordictrack_2450:
                y1Speed = 807;      //vertical position of slider at 2.0
                y1Inclination = 717;    //vertical position of slider at 0.0
            case x14i:
                y1Speed = 785;      //vertical position of slider at 2.0
                y1Inclination = 645;    //vertical position of slider at 0.0
                break;                
            case nordictrack_2950:
            case nordictrack_2950_maxspeed22:
                y1Speed = 807;      //vertical position of slider at 2.0
                y1Inclination = 717;    //vertical position of slider at 0.0
                break;
            case proform_2000:
                y1Speed = 598;      //vertical position of slider at 2.0
                y1Inclination = 522;    //vertical position of slider at 0.0
                break;
            case s22i:
                lastReqResistance = 0;
                y1Resistance = 618;
                break;
            case s22i_NTEX02121_5:
                lastReqResistance = 0;
                y1Resistance = 535;
                break;
            case tdf10:
                lastReqResistance = 1;
                y1Resistance = 604;
                break;
            case proform_studio_bike_pro22:
                lastReqResistance = 1;
                y1Resistance = 805;
                break;                				
            case t85s:
                y1Speed = 609;      //vertical position of slider at 2.0
                y1Inclination = 609;    //vertical position of slider at 0.0
            case s40:
                y1Speed = 482;      //vertical position of slider at 2.0
                y1Inclination = 490;    //vertical position of slider at 0.0
                break;
            case exp7i:
                y1Speed = 430;      //vertical position of slider at 2.0
                y1Inclination = 442;    //vertical position of slider at 0.0
                break;
            case x32i:
                y1Speed = 927;      //vertical position of slider at 2.0
                y1Inclination = 881;    //vertical position of slider at 0.0
                break;
            case x32i_NTL39019:
                y1Speed = 779;      //vertical position of slider at 2.0
                y1Inclination = 740;    //vertical position of slider at 0.0
                break;          
            case x32i_NTL39221:
                y1Speed = 579;      //vertical position of slider at 2.0
                y1Inclination = 635;    //vertical position of slider at 0.0
                break;                                
            case t65s:
                y1Speed = 495;      //vertical position of slider at 2.0
                y1Inclination = 585;    //vertical position of slider at 0.0                
                break;
            case t75s:
                y1Speed = 495;      //vertical position of slider at 2.0
                y1Inclination = 585;    //vertical position of slider at 0.0                
                break;                
            case grand_tour_pro:
                y1Speed = 495;      //vertical position of slider at 2.0
                y1Inclination = 585;    //vertical position of slider at 0.0                
                break;                                
            case c1750:
                y1Speed = 793;      //vertical position of slider at 2.0
                y1Inclination = 694;    //vertical position of slider at 0.0                
                break;                      
            case c1750_2021:
                y1Speed = 592;      //vertical position of slider at 2.0
                y1Inclination = 547;    //vertical position of slider at 0.0                
            case c1750_2020:
                y1Speed = 575;      //vertical position of slider at 1.0
                y1Inclination = 525;    //vertical position of slider at 0.0                                
                break;              
            case elite1000:
                y1Speed = 600;      //vertical position of slider at 1.0
                y1Inclination = 600;    //vertical position of slider at 0.0                                
                break;                                                      
            case NTEX71021:
                y1Resistance = 480;      //vertical position of slider at 1.0
                break;                      
            default:
                break;
        }
        device = dev;
    }

    private void writeLog(String command) {
        MainActivity.writeLog(command);
        Log.i(LOG_TAG, command);
    }

    private void listenAndWaitAndThrowIntent(InetAddress broadcastIP, Integer port) throws Exception {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MyApp::MyWakelockTag");
        wakeLock.acquire();

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
        char decimalSeparator = symbols.getDecimalSeparator();

        byte[] recvBuf = new byte[15000];
        if (socket == null || socket.isClosed()) {
            socket = new DatagramSocket(port);
            socket.setBroadcast(true);
        }
        //socket.setSoTimeout(1000);
        DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
        writeLog("Waiting for UDP broadcast");
        socket.receive(packet);

        String senderIP = packet.getAddress().getHostAddress();
        String message = new String(packet.getData()).trim();

        writeLog("Got UDP broadcast from " + senderIP + ", message: " + message);

        writeLog(message);
        String[] amessage = message.split(";");
        if(device == _device.s22i || device == _device.s22i_NTEX02121_5 || device == _device.tdf10 || device == _device.proform_studio_bike_pro22) {
            if (amessage.length > 0) {
                String rResistance = amessage[0];
                if(decimalSeparator != '.') {
                    rResistance = rResistance.replace('.', decimalSeparator);
                }
                double reqResistance = Double.parseDouble(rResistance);
                reqResistance = Math.round((reqResistance) * 10) / 10.0;
                writeLog("requestResistance: " + reqResistance + " " + lastReqResistance);

                if (lastSwipeMs + 500 < Calendar.getInstance().getTimeInMillis()) {
                    if (reqResistance != -1 && reqResistance != -100 && lastReqResistance != reqResistance || (reqCachedResistance != -1 && reqCachedResistance != -100)) {
                        if (reqCachedResistance != -1 && reqCachedResistance != -100) {
                            reqResistance = reqCachedResistance;
                        }
                        int x1 = 0;
                        int y2 = 0;
                        if (device == _device.s22i) {
                            x1 = 75;
                            y2 = (int) (616.18 - (17.223 * reqResistance));
                        } else if (device == _device.s22i_NTEX02121_5) {
							x1 = 75;
                            writeLog("lastInclinationFloat " + QZService.lastInclinationFloat);
                            y1Resistance = 800 - (int) ((QZService.lastInclinationFloat + 10) * 19);
                            //set speed slider to target position
                            y2 = y1Resistance - (int) ((reqResistance - QZService.lastInclinationFloat) * 19);
                        } else if (device == _device.tdf10) {
							x1 = 1205;
                            y2 = (int) (619.91 - (15.913 * reqResistance));
                        } else if (device == _device.proform_studio_bike_pro22) {
							x1 = 1828;
                            y2 = (int) (826.25 - (21.25 * reqResistance));
                        } else if (device == _device.NTEX71021) {
                            x1 = 950;
                            y2 = (int) (493 - (13.57 * reqResistance));                            
						} else {
							x1 = 1828;
                            y2 = (int) (826.25 - (21.25 * reqResistance));
						}

                        String command = "input swipe " + x1 + " " + y1Resistance + " " + x1 + " " + y2 + " 200";
                        MainActivity.sendCommand(command);
                        writeLog(command);

                        if (device == _device.s22i || device == _device.s22i_NTEX02121_5 || device == _device.tdf10 || device == _device.proform_studio_bike_pro22 || device == _device.NTEX71021)
                            y1Resistance = y2;  //set new vertical position of speed slider
                        lastReqResistance = reqResistance;
                        lastSwipeMs = Calendar.getInstance().getTimeInMillis();
                        reqCachedResistance = -1;
                    }
                } else {
                    reqCachedResistance = reqResistance;
                }
            }
        } else {
            if (amessage.length > 0) {
                String rSpeed = amessage[0];
                if(decimalSeparator != '.') {
                    rSpeed = rSpeed.replace('.', decimalSeparator);
                }

                double reqSpeed = Double.parseDouble(rSpeed);
                reqSpeed = Math.round((reqSpeed) * 10) / 10.0;
                writeLog("requestSpeed: " + reqSpeed + " lastSpeed:" + QZService.lastSpeedFloat + " cachedSpeed:" + reqCachedSpeed);

                if (lastSwipeMs + 500 < Calendar.getInstance().getTimeInMillis() && QZService.lastSpeedFloat > 0) {
                    if (reqSpeed != -1 || reqCachedSpeed != -1) {
                        if (reqCachedSpeed != -1) {
                            reqSpeed = reqCachedSpeed;
                        }
                        int x1 = 0;
                        int y2 = 0;
                        if (device == _device.x11i) {
                            x1 = 1207;
                            y2 = (int) (621.997 - (21.785 * reqSpeed));
                        } else if (device == _device.x22i) {
                            x1 = 1845;
                            y2 = (int) (785 - (23.636363636363636 * reqSpeed));                            
                        } else if (device == _device.x14i) {
                            x1 = 1845;
                            y2 = (int) (785 - (46.63 * reqSpeed * 0.621371));                                                        
                        } else if (device == _device.x32i) {
                            x1 = 1845;
                            y2 = (int) (834.85 - (26.946 * reqSpeed));
                        } else if (device == _device.x32i_NTL39019) {
                            x1 = 1845;
                            y2 = (int) (817.5 - (42.5 * reqSpeed * 0.621371));                            
						} else if (device == _device.t85s) {
                            x1 = 1207;
                            y2 = (int) (629.81 - (20.81 * reqSpeed));
                        } else if (device == _device.s40) {
                            x1 = 949;
                            y2 = (int) (507 - (12.5 * reqSpeed));
                        } else if (device == _device.exp7i) {
                            x1 = 950;
                            y2 = (int) (453.014 - (22.702 * reqSpeed * 0.621371));
                        } else if (device == _device.t65s) {
                            x1 = 1205;
                            y2 = (int) (578.36 - (35.866 * reqSpeed * 0.621371));   
                        } else if (device == _device.t75s) {
                            x1 = 1205;
                            y2 = (int) (578.36 - (35.866 * reqSpeed * 0.621371));                                                        
                        } else if (device == _device.grand_tour_pro) {
                            x1 = 1205;
                            y2 = (int) (578.36 - (35.866 * reqSpeed * 0.621371));                                                                                    
                        } else if (device == _device.nordictrack_2950) {
                            x1 = 1845;     //middle of slider
                            y1Speed = 807 - (int) ((QZService.lastSpeedFloat - 1) * 31);
                            //set speed slider to target position
                            y2 = y1Speed - (int) ((reqSpeed - QZService.lastSpeedFloat) * 31);
                        } else if (device == _device.x32i_NTL39221) {
                            x1 = 1845;     //middle of slider
                            y1Speed = 807 - (int) (((QZService.lastSpeedFloat * 0.621371) - 1) * 46.63);
                            //set speed slider to target position
                            y2 = y1Speed - (int) (((reqSpeed * 0.621371) - (QZService.lastSpeedFloat * 0.621371) - 1) * 46.63);
                        } else if (device == _device.nordictrack_2950_maxspeed22) {
                            x1 = 1845;     //middle of slider
                            y1Speed = 682 - (int) ((QZService.lastSpeedFloat - 1) * 26.5);
                            //set speed slider to target position
                            y2 = y1Speed - (int) ((reqSpeed - QZService.lastSpeedFloat) * 26.5);                            
                        } else if (device == _device.proform_2000) {
                            x1 = 1205;     //middle of slider
                            y2 = (int) ((-19.921 * reqSpeed) + 631.03);
                        } else if (device == _device.c1750) {
                            x1 = 1845;     //middle of slider
                            y1Speed = 785 - (int) ((QZService.lastSpeedFloat - 1) * 31.42);
                            y2 = y1Speed - (int) ((reqSpeed - QZService.lastSpeedFloat) * 31.42);
                        } else if (device == _device.c1750_2021) {
                            x1 = 1205;     //middle of slider
                            y1Speed = 620 - (int) ((QZService.lastSpeedFloat - 1) * 20.73);
                            y2 = y1Speed - (int) ((reqSpeed - QZService.lastSpeedFloat) * 20.73);                            
                        } else if (device == _device.c1750_2020) {
                            x1 = 1205;     //middle of slider
                            y1Speed = 575 - (int) (((QZService.lastSpeedFloat * 0.621371) - 1) * 28.91);
                            y2 = y1Speed - (int) (((reqSpeed * 0.621371) - (QZService.lastSpeedFloat * 0.621371)) * 28.91);
                        } else if (device == _device.nordictrack_2450) {
                            x1 = 1845;     //middle of slider
                            y1Speed = 790 - (int) (((QZService.lastSpeedFloat * 0.621371) - 1) * 46.36);
                            y2 = y1Speed - (int) (((reqSpeed * 0.621371) - (QZService.lastSpeedFloat * 0.621371)) * 46.36);                            
                            y2 = y1Speed - (int) (((reqSpeed * 0.621371) - (QZService.lastSpeedFloat * 0.621371)) * 28.91);                                                        
                        } else if (device == _device.elite1000) {
                            x1 = 1209;     //middle of slider
                            y1Speed = 600 - (int) (((QZService.lastSpeedFloat * 0.621371)) * 31.33);
                            y2 = y1Speed - (int) (((reqSpeed * 0.621371) - (QZService.lastSpeedFloat * 0.621371)) * 31.33);                                                                                    
                        } else {
                            x1 = 1205;     //middle of slider
                            y2 = (int) ((-19.921 * reqSpeed) + 631.03);
                        }

                        String command = "input swipe " + x1 + " " + y1Speed + " " + x1 + " " + y2 + " 200";
                        if(device == _device.x22i || device == _device.x14i) {
                            shellRuntime.exec(command);
                        }
                        else {
                            MainActivity.sendCommand(command);
                        }
                        writeLog(command);

                        if (device == _device.x11i || device == _device.nordictrack_2450 || device == _device.x14i || device == _device.x22i || device == _device.elite1000 || device == _device.c1750 || device == _device.c1750_2021 || device == _device.c1750_2020 || device == _device.proform_2000 || device == _device.t85s || device == _device.t65s || device == _device.grand_tour_pro || device == _device.t75s || device == _device.s40 || device == _device.exp7i || device == _device.x32i || device == _device.x32i_NTL39019 || device == _device.x32i_NTL39221)
                            y1Speed = y2;  //set new vertical position of speed slider
                        lastSwipeMs = Calendar.getInstance().getTimeInMillis();
                        reqCachedSpeed = -1;
                    }
                } else {
                    reqCachedSpeed = reqSpeed;
                }
            }

            if (amessage.length > 1 && lastSwipeMs + 500 < Calendar.getInstance().getTimeInMillis()) {
                String rInclination = amessage[1];
                if(decimalSeparator != '.') {
                    rInclination = rInclination.replace('.', decimalSeparator);
                }
                double reqInclination = roundToHalf(Double.parseDouble(rInclination));
                writeLog("requestInclination: " + reqInclination + " " + reqCachedInclination);				
				Boolean need = reqInclination != -100;
				if (!need && reqCachedInclination != -100) {
					reqInclination = reqCachedInclination;
					reqCachedInclination = -100;
				}					
                if (reqInclination != -100) {
                    int x1 = 0;
                    int y2 = 0;
                    if (device == _device.x11i) {
                        x1 = 75;
                        y2 = (int) (565.491 - (8.44 * reqInclination));
                    } else if (device == _device.x22i) {
                        x1 = 75;
                        y2 = (int) (785 - (11.304347826086957 * (reqInclination + 6)));                        
                    } else if (device == _device.x14i) {
                        x1 = 75;
                        y2 = (int) (785 - (11.304347826086957 * (reqInclination + 6)));                                                
                    } else if (device == _device.x32i) {
                        x1 = 76;
                        y2 = (int) (734.07 - (12.297 * reqInclination));
                    } else if (device == _device.x32i_NTL39019) {
                        x1 = 74;
                        y2 = (int) (749 - (11.8424 * reqInclination));                        
					} else if (device == _device.t85s) {
                        x1 = 75;
                        y2 = (int) (609 - (36.417 * reqInclination));
                    } else if (device == _device.s40) {
                        x1 = 75;
                        y2 = (int) (490 - (21.4 * reqInclination));
                    } else if (device == _device.exp7i) {
                        x1 = 74;
                        y2 = (int) (441.813 - (21.802 * reqInclination));
                    } else if (device == _device.t65s) {
                        x1 = 74;
                        y2 = (int) (576.91 - (34.182 * reqInclination));                        
                    } else if (device == _device.t75s) {
                        x1 = 74;
                        y2 = (int) (576.91 - (34.182 * reqInclination));                                                
                    } else if (device == _device.grand_tour_pro) {
                        x1 = 74;
                        y2 = (int) (576.91 - (34.182 * reqInclination));                                                                        
                    } else if (device == _device.nordictrack_2950 || device == _device.nordictrack_2950_maxspeed22) {
                        x1 = 75;     //middle of slider
                        y1Inclination = 807 - (int) ((QZService.lastInclinationFloat + 3) * 31.1);
                        //set speed slider to target position
                        y2 = y1Inclination - (int) ((reqInclination - QZService.lastInclinationFloat) * 31.1);
                    } else if (device == _device.x32i_NTL39221) {
                        x1 = 75;     //middle of slider
                        y1Inclination = 750 - (int) ((QZService.lastInclinationFloat) * 12.05);
                        //set speed slider to target position
                        y2 = y1Inclination - (int) ((reqInclination - QZService.lastInclinationFloat) * 12.05);
                    } else if (device == _device.proform_2000) {
                        x1 = 79;
                        y1Inclination = 520 - (int) ((QZService.lastInclinationFloat + 3) * 21.804);
                        y2 = y1Inclination - (int) ((reqInclination - QZService.lastInclinationFloat) * 21.804);
                    } else if (device == _device.nordictrack_2450) {
                        x1 = 72;
                        y1Inclination = 715 - (int) ((QZService.lastInclinationFloat + 3) * 29.26);
                        y2 = y1Inclination - (int) ((reqInclination - QZService.lastInclinationFloat) * 29.26);
                    } else if (device == _device.c1750) {
                        x1 = 79;
                        y2 = (int) ((-34.9 * reqInclination) + 700);                        
                    } else if (device == _device.c1750_2021) {
                        x1 = 79;
                        y2 = (int) ((-22 * reqInclination) + 553);                                                
                    } else if (device == _device.c1750_2020) {
                        x1 = 75;
                        y1Inclination = 520 - (int) (QZService.lastInclinationFloat * 20);
                        y2 = y1Inclination - (int) ((reqInclination - QZService.lastInclinationFloat) * 20);
                    } else if (device == _device.elite1000) {
                        x1 = 76;
                        y1Inclination = 589 - (int) (QZService.lastInclinationFloat * 32.8);
                        y2 = y1Inclination - (int) ((reqInclination - QZService.lastInclinationFloat) * 32.8);
                    } else {
                        x1 = 79;
                        y2 = (int) ((-21.804 * reqInclination) + 520.11);
                    }

                    String command = " input swipe " + x1 + " " + y1Inclination + " " + x1 + " " + y2 + " 200";
                    if(device == _device.x22i || device == _device.x14i) {
                        shellRuntime.exec(command);
                    } else {
                        MainActivity.sendCommand(command);
                    }
                    writeLog(command);

                    if (device == _device.x11i || device == _device.nordictrack_2450 || device == _device.elite1000 || device == _device.x22i || device == _device.x14i || device == _device.c1750 || device == _device.c1750_2021 || device == _device.c1750_2020 || device == _device.proform_2000 || device == _device.t85s || device == _device.t65s || device == _device.t75s || device == _device.grand_tour_pro || device == _device.s40 || device == _device.exp7i || device == _device.x32i || device == _device.x32i_NTL39221)
                        y1Inclination = y2;  //set new vertical position of inclination slider
                    lastSwipeMs = Calendar.getInstance().getTimeInMillis();
					reqCachedInclination = -100;
                }
            } else if(amessage.length > 1) {
                String rInclination = amessage[1];
                double reqInclination = roundToHalf(Double.parseDouble(rInclination));
                if(reqInclination != -100) {
                    writeLog("requestInclination not handled due to lastSwipeMs: " + reqInclination);
                    reqCachedInclination = reqInclination;
                }
			}
        }

        broadcastIntent(senderIP, message);
        //socket.close();
    }

    private double roundToHalf(double d) {
        return Math.round(d * 2) / 2.0;
    }

    private void broadcastIntent(String senderIP, String message) {
        Intent intent = new Intent(UDPListenerService.UDP_BROADCAST);
        intent.putExtra("sender", senderIP);
        intent.putExtra("message", message);
        sendBroadcast(intent);
    }

    Thread UDPBroadcastThread;

    InetAddress getBroadcastAddress() throws IOException {
        WifiManager wifi = (WifiManager)    getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcp = null;
        try {
            dhcp = wifi.getDhcpInfo();
            int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
            byte[] quads = new byte[4];
            for (int k = 0; k < 4; k++)
                quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
            return InetAddress.getByAddress(quads);
        } catch (Exception e) {
            Log.e(LOG_TAG, "IOException: " + e.getMessage());
        }
        byte[] quads = new byte[4];
        return InetAddress.getByAddress(quads);
    }

    void startListenForUDPBroadcast() {
        UDPBroadcastThread = new Thread(new Runnable() {
            public void run() {
				{					
					try {
						InetAddress broadcastIP = getBroadcastAddress();
						Integer port = 8003;
						while (shouldRestartSocketListen) {
							listenAndWaitAndThrowIntent(broadcastIP, port);
						}
						//if (!shouldListenForUDPBroadcast) throw new ThreadDeath();
					} catch (Exception e) {
                        writeLog("no longer listening for UDP broadcasts cause of error " + e.getMessage());
					}
				}
            }
        });
        UDPBroadcastThread.start();
    }

    private Boolean shouldRestartSocketListen=true;

    void stopListen() {
        shouldRestartSocketListen = false;
        socket.close();
    }

    @Override
    public void onCreate() {
    }

    @Override
    public void onDestroy() {
        stopListen();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        shouldRestartSocketListen = true;
        startListenForUDPBroadcast();
        writeLog("Service started");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
