package com.thebeastshop.liteflow.parser;

import com.thebeastshop.liteflow.core.NodeComponent;
import com.thebeastshop.liteflow.entity.config.*;
import com.thebeastshop.liteflow.flow.FlowBus;
import com.thebeastshop.liteflow.spring.ComponentScaner;
import com.thebeastshop.liteflow.util.Dom4JReader;
import com.thebeastshop.liteflow.util.PatternUtil;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.Document;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.regex.Matcher;

public abstract class XmlFlowParser {
	
	private final Logger LOG = LoggerFactory.getLogger(XmlFlowParser.class);
	
	public abstract void parseMain(String path) throws Exception;
	
	public void parse(String content) throws Exception {
		Document document = Dom4JReader.getFormatDocument(content);
		parse(document);
	}

	@SuppressWarnings("unchecked")
    private void parse(Document document) throws Exception {
		try {
			Element rootElement = document.getRootElement();

			//判断是以spring方式注册节点，还是以xml方式注册
			if(ComponentScaner.nodeComponentMap.isEmpty()){
				// 解析node节点
				List<Element> nodeList = rootElement.element("nodes").elements("node");
				String id;
				String clazz;
				Node node;
				NodeComponent component;
				for (Element e : nodeList) {
					node = new Node();
					id = e.attributeValue("id");
					clazz = e.attributeValue("class");
					node.setId(id);
					node.setClazz(clazz);
					component = (NodeComponent) Class.forName(clazz).newInstance();
					if (component == null) {
						LOG.error("couldn't find component class [{}] ", clazz);
					}
					Objects.requireNonNull(component).setNodeId(id);
					node.setInstance(component);
					FlowBus.addNode(id, node);
				}
			}else{
				for(Entry<String, NodeComponent> componentEntry : ComponentScaner.nodeComponentMap.entrySet()){
					FlowBus.addNode(componentEntry.getKey(), new Node(componentEntry.getKey(), componentEntry.getValue().getClass().getName(), componentEntry.getValue()));
				}
			}

			// 解析chain节点
			String chainName;
			String condArrayStr;
			String[] condArray;
			List<Node> chainNodeList;
			List<Condition> conditionList;

			List<Element> chainList = rootElement.elements("chain");
			for (Element e : chainList) {
				chainName = e.attributeValue("name");
				conditionList = new ArrayList<>();
				for (Iterator<Element> it = e.elementIterator(); it.hasNext();) {
					Element condE = it.next();
					condArrayStr = condE.attributeValue("value");
					if (StringUtils.isBlank(condArrayStr)) {
						continue;
					}
					chainNodeList = new ArrayList<>();
					condArray = condArrayStr.split(",");
					RegexEntity regexEntity;
					Node node;
                    for (String aCondArray : condArray) {
                        regexEntity = parseNodeStr(aCondArray.trim());
                        node = FlowBus.getNode(regexEntity.getCondNode());
                        chainNodeList.add(node);
                        if (regexEntity.getRealNodeArray() != null) {
                            for (String key : regexEntity.getRealNodeArray()) {
                                Node condNode = FlowBus.getNode(key);
                                if (condNode != null) {
                                    node.setCondNode(condNode.getId(), condNode);
                                }
                            }
                        }
                    }
					if ("then".equals(condE.getName())) {
						conditionList.add(new ThenCondition(chainNodeList));
					} else if ("when".equals(condE.getName())) {
						conditionList.add(new WhenCondition(chainNodeList));
					}
				}
				FlowBus.addChain(chainName, new Chain(chainName,conditionList));
			}
		} catch (Exception e) {
			LOG.error("FlowParser parser exception: {}", e);
		}

	}
	
	private static RegexEntity parseNodeStr(String str) {
	    List<String> list = new ArrayList<>();
		Matcher m = PatternUtil.parseNodeStr(str);
	    while(m.find()){
	        list.add(m.group());
	    }
	    RegexEntity regexEntity = new RegexEntity();
	    regexEntity.setCondNode(list.get(0).trim());
	    if(list.size() > 1){
	    	String[] realNodeArray = list.get(1).split("\\|");
	    	for (int i = 0; i < realNodeArray.length; i++) {
	    		realNodeArray[i] = realNodeArray[i].trim();
			}
	    	regexEntity.setRealNodeArray(realNodeArray);
	    }
	    return regexEntity;
	}
}
