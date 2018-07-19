package spider;

import com.xiaoleilu.hutool.collection.CollUtil;
import com.xiaoleilu.hutool.io.FileUtil;
import com.xiaoleilu.hutool.log.Log;
import com.xiaoleilu.hutool.log.LogFactory;
import com.xiaoleilu.hutool.thread.ThreadUtil;
import com.xiaoleilu.hutool.util.CharsetUtil;
import com.xiaoleilu.hutool.util.RandomUtil;
import com.xiaoleilu.hutool.util.StrUtil;
import common.Mm131SpiderConstant;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;
import utils.Mm131SpiderUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * 功能说明：爬虫入口
 *
 * @author Ason
 * @date 2018/07/18
 **/

public class Mm131Spider implements PageProcessor {

    private static final Log logger = LogFactory.get();

    private static Site site = Site.me().setRetryTimes(2).setCycleRetryTimes(2).setSleepTime(1000).setTimeOut(20 * 1000)
                                .setCharset(CharsetUtil.GBK)
                                .setUserAgent(RandomUtil.randomEle(Mm131SpiderConstant.USER_AGENT_LIST));

    private String filePath;
    private String urlCheckName;
    private String imgTypeUrl;
    public Mm131Spider(){
    }

    public Mm131Spider(String filePath, String imgTypeUrl){
        this.filePath = filePath.endsWith("\\") ? filePath : filePath + "\\";
        // 初始化的时候先新建个文件夹
        FileUtil.mkdir(this.filePath);
        this.imgTypeUrl = imgTypeUrl;
        this.urlCheckName = this.filePath + "urlCheck.txt";
    }

    @Override
    public void process(Page page) {
        site.setUserAgent(RandomUtil.randomEle(Mm131SpiderConstant.USER_AGENT_LIST));
        String pageUrl = page.getUrl().toString();
        /**
         * 图集详细页
         */
        if (!pageUrl.contains("_") && pageUrl.contains(".html")) {
            getImgInfo(page);
        } else {
            /**
             * 图集列表页
             */
            getImgList(page);
        }
        return;
    }

    @Override
    public Site getSite() {
        return site;
    }

    /**
     * img列表页
     */
    private void getImgList(Page page){
        String pageUrl = page.getUrl().toString();
        List<String> checkUrlList = new ArrayList<String>();
        Map<String, Map<String, Object>> imgInfoMap = new HashMap<>(16);
        // 获取所有图集的List
        List<String> imgList = page.getHtml().xpath("//*[@class=\"main\"]/dl/dd").all();
        for (int i = 1; i <= imgList.size() - 1; i++) {
            // 图集名称
            String imgTitle = page.getHtml().xpath("//*[@class=\"main\"]/dl/dd[" + i + "]/a/img/@alt").get();
            // 图集的url
            String imgUrl = page.getHtml().xpath("//*[@class=\"main\"]/dl/dd[" + i + "]/a/@href").get();
            // 图集的封面页
            String firstImgUrl = page.getHtml().xpath("//*[@class=\"main\"]/dl/dd[" + i + "]/a/img/@src").get();
            if (!StrUtil.isEmpty(imgUrl) && !StrUtil.isEmpty(imgUrl)){
                String checkUrl = imgUrl.replaceAll(Mm131SpiderConstant.DOMAIN,"");
                checkUrlList.add(checkUrl);
                Map<String, Object> imgInfo = new HashMap<>(16);
                imgInfo.put("imgTitle", imgTitle);
                imgInfo.put("imgUrl", imgUrl);
                imgInfo.put("firstImgUrl", firstImgUrl);
                imgInfoMap.put(checkUrl, imgInfo);
            }
        }
        // 从文件中检查出未爬取的图集
        List<String> spiderUrlList = Mm131SpiderUtil.readFileForCheck(urlCheckName, checkUrlList);
        if (!CollUtil.isEmpty(spiderUrlList)){
            List<Map<String, Object>> imgInfos = new ArrayList<>();
            for (String spiderUrl : spiderUrlList){
                Map<String, Object> imgInfo = imgInfoMap.get(spiderUrl);
                imgInfos.add(imgInfo);
            }
            for (Map<String, Object> imgInfo : imgInfos) {
                String imgUrl = (String)imgInfo.get("imgUrl");
                String imgTitle = (String)imgInfo.get("imgTitle");
                String firstImgUrl = (String)imgInfo.get("firstImgUrl");
                // 下载封面图
                String pathName = Mm131SpiderUtil.downImage(firstImgUrl, filePath + "image\\", imgTitle);
                // 添加待爬取队列
                if (StrUtil.isNotBlank(pathName)){
                    page.addTargetRequest(imgUrl);
                }
            }
        }
        // 下一页
        List<String> nextPageList = page.getHtml().xpath("//*[@class=\"page\"]/a").all();
        String nextPageUrl = null;
        for (int i = 1; i < nextPageList.size(); i++) {
            if ("下一页".equals(page.getHtml().xpath("//*[@class=\"page\"]/a["+ i +"]/text()").get())) {
                nextPageUrl = page.getHtml().xpath("//*[@class=\"page\"]/a["+ i +"]/@href").get();
                break;
            }
        }
        if (!StrUtil.isEmpty(nextPageUrl)){
            nextPageUrl = imgTypeUrl + (nextPageUrl.startsWith("/") ? nextPageUrl : "/" + nextPageUrl);
            // 添加下一页到待爬取队列
            page.addTargetRequest(nextPageUrl);
        }
        logger.info("thisPageUrl is {}, nextPageUrl is {}", pageUrl, nextPageUrl);
        page.setSkip(true);
    }

    /**
     * 图集详情
     */
    private void getImgInfo(Page page) {
        String imgCountStr = page.getHtml().xpath("//*[@class=\"content-page\"]/span[1]/text()").get();
        // 图集的图片总数
        Integer imgCount = Integer.valueOf(imgCountStr.substring(1, imgCountStr.length() - 1));
        String pageUrl = page.getUrl().toString();
        String pageUrlCode = pageUrl.substring(pageUrl.lastIndexOf("/"), pageUrl.lastIndexOf("."));
        String imgTitle = page.getHtml().xpath("//*[@class=\"content\"]/h5/text()").get();
        String imgPath = this.filePath + imgTitle + "\\";
        FileUtil.mkdir(imgPath);
        if (FileUtil.ls(imgPath).length != imgCount) {
            String imgUrl;
            for (Integer i = 1; i <= imgCount; i++) {
                imgUrl = Mm131SpiderConstant.IMG_DOMAIN + pageUrlCode + "/" + i + ".jpg";
                String pathName = Mm131SpiderUtil.downImage(imgUrl, imgPath, imgTitle + " - " + i);
                if (StrUtil.isBlank(pathName)) {
                    return;
                }
            }
        }
        // 下载完就将图集保存进文件，下一次就不用再爬取啦
        FileUtil.appendUtf8String(pageUrl.replaceAll(Mm131SpiderConstant.DOMAIN,"") + "\r\n", urlCheckName);

    }

    public static void main(String[] args) {
        //  创建一个线程池
        ExecutorService pool = ThreadUtil.newExecutor(6);
        for (int i = 0; i < Mm131SpiderConstant.IMG_TYPES_URL.size(); i++) {
            // 图集类型url，总共有6种类型
            String imgTypeUrl = Mm131SpiderConstant.IMG_TYPES_URL.get(i);
            // 图片保存路径，不存在则会自动创建
            String filePath = "F:\\爬虫\\" + imgTypeUrl.substring(imgTypeUrl.lastIndexOf("/") + 1);

            pool.execute(() -> {
                // 启动爬虫
                Spider.create(new Mm131Spider(filePath, imgTypeUrl))
                        // 添加爬取的url
                        .addUrl(imgTypeUrl)
                        // 多线程爬取
                        .thread(5)
                        .run();
            });
        }
        pool.shutdown();
    }
}





















