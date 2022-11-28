package com.ltyzzz.core.registry.zookeeper;

import com.ltyzzz.core.event.IRpcEvent;
import com.ltyzzz.core.event.IRpcListenerLoader;
import com.ltyzzz.core.event.IRpcUpdateEvent;
import com.ltyzzz.core.event.URLChangeWrapper;
import com.ltyzzz.core.registry.RegistryService;
import com.ltyzzz.core.registry.URL;
import com.ltyzzz.core.service.DataService;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

import java.util.List;

public class ZookeeperRegister extends AbstractRegister implements RegistryService {

    private AbstractZookeeperClient zkClient;

    private String ROOT = "/irpc";

    public ZookeeperRegister(String address) {
        this.zkClient = new CuratorZookeeperClient(address);
    }

    private String getProviderPath(URL url) {
        return ROOT + "/" + url.getServiceName() +
                "/provider/" + url.getParameters().get("host") +
                ":" + url.getParameters().get("port");
    }

    private String getConsumerPath(URL url) {
        return ROOT + "/" + url.getServiceName() +
                "/consumer/" + url.getApplicationName() +
                ":" + url.getParameters().get("host") + ":";
    }

    @Override
    public void register(URL url) {
        if (!zkClient.existNode(ROOT)) {
            zkClient.createPersistentData(ROOT, "");
        }
        String urlStr = URL.buildProviderUrlStr(url);
        if (zkClient.existNode(getProviderPath(url))) {
            zkClient.deleteNode(getProviderPath(url));
        }
        zkClient.createTemporaryData(getProviderPath(url), urlStr);
        super.register(url);
    }

    @Override
    public void unRegister(URL url) {
        zkClient.deleteNode(getProviderPath(url));
        super.unRegister(url);
    }

    @Override
    public void subscribe(URL url) {
        if (!this.zkClient.existNode(ROOT)) {
            zkClient.createPersistentData(ROOT, "");
        }
        String urlStr = URL.buildConsumerUrlStr(url);
        if (zkClient.existNode(getConsumerPath(url))) {
            zkClient.deleteNode(getConsumerPath(url));
        }
        zkClient.createTemporarySeqData(getConsumerPath(url), urlStr);
        super.subscribe(url);
    }

    @Override
    public void unSubscribe(URL url) {
        zkClient.deleteNode(getConsumerPath(url));
        super.unSubscribe(url);
    }

    @Override
    public void doAfterSubscribe(URL url) {
        String newServerNodePath = ROOT + "/" + url.getServiceName() + "/provider";
        watchChildNodeData(newServerNodePath);
    }

    public void watchChildNodeData(String newServerNodePath) {
        zkClient.watchChildNodeData(newServerNodePath, new Watcher() {
            @Override
            public void process(WatchedEvent watchedEvent) {
                System.out.println(watchedEvent);
                String path = watchedEvent.getPath();
                List<String> childrenData = zkClient.getChildrenData(path);
                URLChangeWrapper urlChangeWrapper = new URLChangeWrapper();
                urlChangeWrapper.setProviderUrl(childrenData);
                urlChangeWrapper.setServiceName(path.split("/")[2]);
                IRpcEvent iRpcEvent = new IRpcUpdateEvent(urlChangeWrapper);
                IRpcListenerLoader.sendEvent(iRpcEvent);
                watchChildNodeData(path);
            }
        });
    }


    @Override
    public void doBeforeSubscribe(URL url) {

    }

    @Override
    public List<String> getProviderIps(String serviceName) {
        List<String> nodeDataList = this.zkClient.getChildrenData(ROOT + "/" + serviceName + "/provider");
        return nodeDataList;
    }

    public static void main(String[] args) throws InterruptedException {
        ZookeeperRegister zookeeperRegister = new ZookeeperRegister("localhost:2181");
        List<String> urls = zookeeperRegister.getProviderIps(DataService.class.getName());
        System.out.println(urls);
        Thread.sleep(2000000);
    }
}