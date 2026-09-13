package home.doorble;

import android.content.Context;
import android.content.pm.*;
import android.os.Build;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;

final class UpdateFiles {
    static HttpsURLConnection open(String address)throws Exception{
        for(int redirects=0;redirects<6;redirects++){
            URL url=new URL(address);String host=url.getHost();
            if(!"https".equals(url.getProtocol())||url.getUserInfo()!=null||(url.getPort()!=-1&&url.getPort()!=443)||!Arrays.asList("github.com","release-assets.githubusercontent.com","objects.githubusercontent.com").contains(host))throw new IOException("Unexpected update host");
            HttpsURLConnection c=(HttpsURLConnection)url.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setRequestProperty("User-Agent","Door-Android-Updater");c.setRequestProperty("Accept-Encoding","identity");
            int status=c.getResponseCode();
            if(status==301||status==302||status==303||status==307||status==308){String location=c.getHeaderField("Location");c.disconnect();if(location==null)throw new IOException("Missing update redirect");address=new URL(url,location).toString();continue;}
            if(status!=200){c.disconnect();throw new IOException(status==404?"No app release is available yet":"GitHub returned HTTP "+status);}return c;
        }
        throw new IOException("Too many update redirects");
    }
    static String manifest()throws Exception{
        HttpsURLConnection c=open(UpdateRelease.FEED);
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>32768)throw new IOException("Update metadata is too large");out.write(b,0,n);}return out.toString("UTF-8");
        }finally{c.disconnect();}
    }
    static File apk(Context c){return new File(c.getCacheDir(),"updates/door.apk");}
    static void download(Context context,UpdateRelease release)throws Exception{
        File target=apk(context);if(!target.getParentFile().isDirectory()&&!target.getParentFile().mkdirs())throw new IOException("Cannot create update cache");
        File partial=new File(target.getParentFile(),"download.part");HttpsURLConnection c=open(release.apkUrl);
        try{
            long total=0;try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(partial)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1){total+=n;if(total>release.bytes)throw new IOException("Unexpected APK size");out.write(b,0,n);}out.getFD().sync();}
            verify(context,partial,release);
            if(target.exists()&&!target.delete())throw new IOException("Cannot replace cached update");
            if(!partial.renameTo(target))throw new IOException("Cannot save verified update");
        }finally{c.disconnect();if(partial.exists())partial.delete();}
    }
    @SuppressWarnings("deprecation") static Signature[] signatures(PackageInfo p){return Build.VERSION.SDK_INT>=28?p.signingInfo==null?null:p.signingInfo.getApkContentsSigners():p.signatures;}
    @SuppressWarnings("deprecation") static int signatureFlags(){return Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;}
    @SuppressWarnings("deprecation") static long version(PackageInfo p){return Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;}
    static long installed(Context c)throws Exception{return version(c.getPackageManager().getPackageInfo(c.getPackageName(),0));}
    static String installedName(Context c){try{return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}catch(Exception e){return "unknown";}}
    static void verify(Context c,File file,UpdateRelease release)throws Exception{
        if(file.length()!=release.bytes)throw new IOException("APK size check failed");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}
        if(!MessageDigest.isEqual(digest.digest(),Protocol.unhex(release.sha256)))throw new IOException("APK checksum check failed");
        PackageManager pm=c.getPackageManager();PackageInfo candidate=pm.getPackageArchiveInfo(file.getPath(),signatureFlags());PackageInfo current=pm.getPackageInfo(c.getPackageName(),signatureFlags());
        if(candidate==null||!c.getPackageName().equals(candidate.packageName)||version(candidate)!=release.versionCode||version(candidate)<=version(current)||!release.versionName.equals(candidate.versionName)||candidate.applicationInfo==null||candidate.applicationInfo.minSdkVersion>Build.VERSION.SDK_INT)throw new IOException("APK package or version check failed");
        Signature[] a=signatures(candidate),b=signatures(current);
        if(a==null||b==null||a.length!=1||b.length!=1||!a[0].equals(b[0]))throw new IOException("APK signing identity does not match this installation");
    }
}
