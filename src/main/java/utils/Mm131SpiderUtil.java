package utils;

import com.xiaoleilu.hutool.io.file.FileReader;
import com.xiaoleilu.hutool.log.Log;
import com.xiaoleilu.hutool.log.LogFactory;
import com.xiaoleilu.hutool.util.RandomUtil;
import com.xiaoleilu.hutool.util.StrUtil;
import common.Mm131SpiderConstant;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * 功能说明：爬虫工具类
 *
 * @author Ason
 * @date 2018/07/18
 **/
public class Mm131SpiderUtil {

    private static final Log logger = LogFactory.get();

    /**
     * url排重
     *
     * @param pathName   绝对路径文件名
     * @param urlList    需要排重的url列表
     * @return
     */
    public static List<String> readFileForCheck(String pathName, List<String> urlList) {
        File file = new File(pathName);
        if(file.exists()) {
            FileReader fileReader = new FileReader(file);
            fileReader.readLines().forEach(line -> {
                urlList.remove(line);
            });
        }
        logger.warn("排重后需要爬取的图集数为：" + urlList.size());
        return urlList;
    }

    /**
     * 下载图片
     *
     * @param imgUrl    图片链接
     * @param filePath  文件路径
     * @param fileName  文件名
     */
    public static String downImage(String imgUrl, String filePath, String fileName){
        // 过滤不符合命名的规则
        fileName = fileName.replaceAll("[\\\\/:*?\"<>|]","");
        String pathName = filePath + fileName + getFileSuffix(imgUrl);
        // 已经存在的就不用再下载啦
        if (com.xiaoleilu.hutool.io.FileUtil.exist(pathName)) {
            return pathName;
        }
        File fileDir = new File(filePath);
        if(!fileDir.exists()) {
            fileDir.mkdirs();
        }
        Integer downCount = 3;
        // 重试
        for (int i = 1; i <= downCount; i++) {
            try {
                if (downImage(imgUrl, pathName)) {
                    return pathName;
                }
            } catch (Exception e) {
                logger.error("图片第{}次下载失败,图片URL：{}", i, imgUrl);
            }
        }
        return null;
    }

    /**
     * 根据文件名获取文件尾缀，包括.
     *
     * @param fileName 文件名
     * @return         文件尾缀，包括.，如.jpg
     */
    public static String getFileSuffix(String fileName){
        if (StrUtil.isNotBlank(fileName)){
            int start = fileName.lastIndexOf(".");
            if (start != -1){
                return fileName.substring(start);
            }
        }
        return null;
    }

    /**
     * 下载图片
     *
     * @param imageUrl 图片url
     * @param pathName 文件保存时的位置
     */
    public static Boolean downImage(String imageUrl, String pathName) throws Exception {
        byte[] btImg = getImageFromNetByUrl(imageUrl);
        if(null != btImg && btImg.length > 0){
            try {
                File file = new File(pathName);
                FileOutputStream fops = new FileOutputStream(file);
                fops.write(btImg);
                fops.flush();
                fops.close();
                logger.info("图片已经写入到：{}", pathName);
                return true;
            } catch (Exception e) {
                logger.error("下载图片异常,图片url为:{}, pathname为{},异常信息{}", imageUrl, pathName, e.getMessage());
            }
        }else{
            logger.error("无数据的字节流.图片url为:{}, pathname为{}", imageUrl, pathName);
        }
        return false;
    }

    /**
     * 根据地址获得数据的字节流
     *
     * @param strUrl 网络连接地址
     * @return
     */
    public static byte[] getImageFromNetByUrl(String strUrl) throws IOException {
        InputStream inStream = null;
        try {
            URL url = new URL(strUrl);
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            // 设置Java服务器代理连接，要不然报错403
            // 浏览器可以访问此url图片并显示，但用Java程序就不行报错Server returned HTTP response code:403 for URL
            // 具体原因：服务器的安全设置不接受Java程序作为客户端访问(被屏蔽)，解决办法是设置客户端的User Agent
            conn.setRequestProperty("User-Agent", RandomUtil.randomEle(Mm131SpiderConstant.USER_AGENT_LIST));
            // 很多网站的防采集的办法,就是判断浏览器来源referer和cookie以及userAgent
            // 参考：https://blog.csdn.net/chengyingzhilian/article/details/7835400
            //       https://blog.csdn.net/wl981292580/article/details/80351136
            conn.setRequestProperty("Referer", strUrl);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(30 * 1000);
            //通过输入流获取图片数据
            inStream = conn.getInputStream();
            //得到图片的二进制数据
            byte[] btImg = readInputStream(inStream);
            return btImg;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (inStream != null){
                inStream.close();
            }
        }
        return null;
    }
    /**
     * 从输入流中获取数据
     *
     * @param inStream 输入流
     * @return
     * @throws Exception
     */
    public static byte[] readInputStream(InputStream inStream) throws Exception{
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len = 0;
        while( (len=inStream.read(buffer)) != -1 ){
            outStream.write(buffer, 0, len);
        }
        inStream.close();
        return outStream.toByteArray();
    }
}
