package com.doyeonism.mappreview;
import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.location.*;
import android.content.pm.PackageManager;
import android.webkit.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class MainActivity extends Activity implements LocationListener {
 private WebView web;
 private LocationManager locations;
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  web=new WebView(this);setContentView(web);
  web.getSettings().setJavaScriptEnabled(true);
  web.getSettings().setDomStorageEnabled(true);
  web.setWebViewClient(new WebViewClient());
  web.setWebChromeClient(new WebChromeClient(){
   @Override public boolean onConsoleMessage(ConsoleMessage m){
    String message=m.message();
    if(!message.contains("access_token")&&!message.contains("pk.")) android.util.Log.i("MapPreview",message);
    return true;
   }
  });
  try(InputStream in=getAssets().open("map.html")) {
   String route; try(InputStream routeIn=getAssets().open("route.json")){route=new String(routeIn.readAllBytes(),StandardCharsets.UTF_8);}
   String html=new String(in.readAllBytes(),StandardCharsets.UTF_8).replace("__MAPBOX_TOKEN__",BuildConfig.MAPBOX_TOKEN).replace("__ROUTE_JSON__",route);
   web.loadDataWithBaseURL("https://appassets.androidplatform.net/",html,"text/html","UTF-8",null);
  }catch(IOException e){throw new RuntimeException(e);}
  locations=(LocationManager)getSystemService(LOCATION_SERVICE);
  if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)startLocation();
  else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},1);
 }
 private void startLocation(){
  for(String provider:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER})try{
   locations.requestLocationUpdates(provider,1000,0,this);
   Location last=locations.getLastKnownLocation(provider);if(last!=null)onLocationChanged(last);
  }catch(SecurityException|IllegalArgumentException ignored){}
 }
 @Override public void onRequestPermissionsResult(int code,String[] p,int[] results){super.onRequestPermissionsResult(code,p,results);if(code==1&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)startLocation();}
 @Override public void onLocationChanged(Location l){
  String js=String.format(Locale.US,"window.updatePosition&&window.updatePosition(%.8f,%.8f,%.2f,%.1f,%d)",l.getLongitude(),l.getLatitude(),l.getSpeed()*3.6,l.getBearing(),Math.max(0,(android.os.SystemClock.elapsedRealtimeNanos()-l.getElapsedRealtimeNanos())/1000000));
  web.evaluateJavascript(js,null);
 }
 @Override protected void onDestroy(){if(locations!=null)locations.removeUpdates(this);web.destroy();super.onDestroy();}
}
