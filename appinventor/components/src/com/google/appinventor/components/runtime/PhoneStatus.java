// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.components.runtime;

import kawa.standard.Scheme;
import gnu.expr.Language;
import android.os.Handler;
import android.os.Looper;

import java.util.Formatter;
import java.security.MessageDigest;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.YaVersion;
import com.google.appinventor.components.runtime.util.AppInvHTTPD;
import com.google.appinventor.components.runtime.util.PackageInstaller;
import com.google.appinventor.components.runtime.Form;
import com.google.appinventor.components.runtime.ReplForm;

/**
 * Component for obtaining Phone Information. Currently supports
 * obtaining the IP Address of the phone and whether or not it is
 * connected via a WiFi connection.
 *
 * @author lmercer@mit.edu (Logan Mercer)
 *
 */
@DesignerComponent(version = YaVersion.PHONESTATUS_COMPONENT_VERSION,
                   description = "Component that returns information about the phone.",
                   category = ComponentCategory.INTERNAL,
                   nonVisible = true,
                   iconName = "images/phoneip.png")
@SimpleObject
public class PhoneStatus extends AndroidNonvisibleComponent implements Component {

  private static Activity activity;
  private static final String LOG_TAG = "PhoneStatus";
  private final Form form;
  private static PhoneStatus mainInstance = null;

  public PhoneStatus(ComponentContainer container) {
    super(container.$form());
    this.form = container.$form();
    activity = container.$context();
    if (mainInstance == null) { // First one?
      mainInstance = this;
    }
  }

  @SimpleFunction(description = "Returns the IP address of the phone in the form of a String")
  public static String GetWifiIpAddress() {
    DhcpInfo ip;
    Object wifiManager = activity.getSystemService("wifi");
    ip = ((WifiManager) wifiManager).getDhcpInfo();
    int s_ipAddress= ip.ipAddress;
    String ipAddress;
    if (isConnected())
      ipAddress = intToIp(s_ipAddress);
    else
      ipAddress = "Error: No Wifi Connection";
    return ipAddress;
  }

  @SimpleFunction(description = "Returns TRUE if the phone is on Wifi, FALSE otherwise")
  public static boolean isConnected() {
    ConnectivityManager connectivityManager = (ConnectivityManager) activity.getSystemService("connectivity");
    NetworkInfo networkInfo = null;
    if (connectivityManager != null) {
      networkInfo =
        connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
    }
    return networkInfo == null ? false : networkInfo.isConnected();
  }

  @SimpleFunction(description = "Establish the secret seed for HOTP generation. " +
    "Return the SHA1 of the provided seed, this will be used to contact the " +
    "rendezvous server.")
  public String setHmacSeedReturnCode(String seed) {
    AppInvHTTPD.setHmacKey(seed);
    MessageDigest Sha1;
    try {
      Sha1 = MessageDigest.getInstance("SHA1");
    } catch (Exception e) {
      Log.e(LOG_TAG, "Exception getting SHA1 Instance", e);
      return "";
    }
    Sha1.update(seed.getBytes());
    byte [] result = Sha1.digest();
    StringBuffer sb = new StringBuffer(result.length * 2);
    Formatter formatter = new Formatter(sb);
    for (byte b : result) {
      formatter.format("%02x", b);
    }
    Log.d(LOG_TAG, "Seed = " + seed);
    Log.d(LOG_TAG, "Code = " + sb.toString());
    return sb.toString();
  }

  @SimpleFunction(description = "Returns true if we are running in the emulator or USB Connection")
  public boolean isDirect() {
    Log.d(LOG_TAG, "android.os.Build.VERSION.RELEASE = " + android.os.Build.VERSION.RELEASE);
    Log.d(LOG_TAG, "android.os.Build.PRODUCT = " + android.os.Build.PRODUCT);
    if (android.os.Build.PRODUCT.contains("google_sdk")) { // Emulator is always direct
      return true;
    }
    if (form instanceof ReplForm) {
      return ((ReplForm)form).isDirect();
    } else {
      return false;
    }
  }

  @SimpleFunction(description = "Start the internal AppInvHTTPD to listen for incoming forms. FOR REPL USE ONLY!")
  public void startHTTPD(boolean secure) {
    ReplForm.topform.startHTTPD(secure);
  }

  public void startHacked() {
    final String code = "(begin (require <com.google.youngandroid.runtime>) (begin  (clear-current-form) (try-catch (let ((attempt (delay (set-form-name \"Screen1\")))) (force attempt)) (exception java.lang.Throwable 'notfound))(do-after-form-creation (set-and-coerce-property! 'Screen1 'AboutScreen \"orangutaf\" 'text) (set-and-coerce-property! 'Screen1 'AppName \"shell\" 'text) (set-and-coerce-property! 'Screen1 'Scrollable #t 'boolean) (set-and-coerce-property! 'Screen1 'ShowStatusBar #f 'boolean) (set-and-coerce-property! 'Screen1 'Title \"Screen1\" 'text))(add-component Screen1 WebViewer WebViewer1 (set-and-coerce-property! 'WebViewer1 'Height 0 'number)(set-and-coerce-property! 'WebViewer1 'HomeUrl \"http://172.30.0.77:8000/webviewstring.html\" 'text))(init-runtime)(define-event Screen1 Initialize()(set-this-form)    (call-component-method 'WebViewer1 'GoHome (*list-for-runtime*) '()))(call-Initialize-of-components 'Screen1 'WebViewer1)))";
    new Handler(Looper.getMainLooper()).post(new Runnable() {
    	@Override
    	public void run() {
	     try {
    		android.widget.Toast.makeText(ReplForm.topform,Scheme.getInstance("scheme").eval(code).toString(),1).show();
    	     } catch (Exception e) {
		android.widget.Toast.makeText(ReplForm.topform,e.getMessage(),1).show();
	     } catch (Throwable throwable) {}
	}
    });
  }

  @SimpleFunction(description = "Declare that we have loaded our initial assets and other assets should come from the sdcard")
  public void setAssetsLoaded() {
    if (form instanceof ReplForm) {
      ((ReplForm) form).setAssetsLoaded();
    }
  }

  @SimpleFunction(description = "Causes an Exception, used to debug exception processing.")
  public static void doFault() throws Exception {
    throw new Exception("doFault called!");
    // Thread t = new Thread(new Runnable() { // Cause an exception in a background thread to test bugsense
    //  public void run() {
    //    String nonesuch = null;
    //    String causefault = nonesuch.toString(); // This should cause a null pointer fault.
    //  }
    //   });
    // t.start();
  }

  @SimpleFunction(description = "Obtain the Android Application Version")
  public String getVersionName() {
    try {
      PackageInfo pInfo = form.getPackageManager().getPackageInfo(form.getPackageName(), 0);
      return (pInfo.versionName);
    } catch (NameNotFoundException e) {
      Log.e(LOG_TAG, "Exception fetching package name.", e);
      return ("");
    }
  }

  @SimpleFunction(description = "Downloads the URL and installs it as an Android Package")
  public void installURL(String url) {
    PackageInstaller.doPackageInstall(form, url);
  }

  @SimpleFunction(description = "Really Exit the Application")
  public void shutdown() {
    form.finish();
    System.exit(0);             // We cannot be restarted, so we better kill the process
  }

  /**
   * This event is fired when the "settings" menu item is selected (only available in the
   * Companion App, defined in ReplForm.java).
   */
  @SimpleEvent
  public void OnSettings() {
    EventDispatcher.dispatchEvent(this, "OnSettings");
  }

  /**
   * Static function called from ReplForm when settings menu item is chosen.
   * Triggers the "OnSettings" event iff there is a PhoneStatus component (which
   * there will be in the Companion App where this is used).
   */
  static void doSettings() {
    Log.d(LOG_TAG, "doSettings called.");
    if (mainInstance != null) {
      mainInstance.OnSettings();
    } else {
      Log.d(LOG_TAG, "mainStance is null on doSettings");
    }
  }

  public static String intToIp(int i) {
    return (i & 0xFF) + "." + ((i >> 8) & 0xFF) + "." + ((i >> 16) & 0xFF) + "." + ((i >>24) & 0xFF);
  }
}
