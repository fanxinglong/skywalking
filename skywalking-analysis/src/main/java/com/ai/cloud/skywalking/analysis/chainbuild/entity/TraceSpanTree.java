package com.ai.cloud.skywalking.analysis.chainbuild.entity;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ai.cloud.skywalking.analysis.chainbuild.exception.BuildTraceSpanTreeException;
import com.ai.cloud.skywalking.analysis.chainbuild.util.StringUtil;
import com.ai.cloud.skywalking.analysis.chainbuild.util.TokenGenerator;
import com.ai.cloud.skywalking.protocol.Span;

public class TraceSpanTree {
	private Logger logger = LoggerFactory.getLogger(TraceSpanTree.class);

	private String userId = null;

	private String cid;

	private TraceSpanNode treeRoot;

	public TraceSpanTree() {
	}

	public String build(List<Span> spanList) throws BuildTraceSpanTreeException {
		if (spanList.size() == 0) {
			throw new BuildTraceSpanTreeException("spanList is empty.");
		}

		Collections.sort(spanList, new Comparator<Span>() {
			@Override
			public int compare(Span span1, Span span2) {
				String span1TraceLevel = span1.getParentLevel() + "."
						+ span1.getLevelId();
				String span2TraceLevel = span2.getParentLevel() + "."
						+ span2.getLevelId();
				return span1TraceLevel.compareTo(span2TraceLevel);
			}
		});
		cid = generateChainToken(spanList.get(0));
		treeRoot = new TraceSpanNode(null, null, null, null, spanList.get(0));
		if (spanList.size() > 1) {
			for (int i = 1; i < spanList.size(); i++) {
				this.build(spanList.get(i));
			}
		}

		return cid;
	}

	private void build(Span span) throws BuildTraceSpanTreeException {
		if (userId == null && span.getUserId() != null) {
			userId = span.getUserId();
		}

		TraceSpanNode clientOrServerNode = findNodeAndCreateVisualNodeIfNess(
				span.getParentLevel(), span.getLevelId());
		if (clientOrServerNode != null) {
			clientOrServerNode.mergeSpan(span);
		}

		if (span.getLevelId() > 0) {
			TraceSpanNode foundNode = findNodeAndCreateVisualNodeIfNess(
					span.getParentLevel(), span.getLevelId() - 1);
			if (foundNode != null) {
				new TraceSpanNode(null, null, foundNode, foundNode.next(), span);
			}
		} else {
			/**
			 * levelId=0 find for parent level if parentLevelId = 0.0.1 then
			 * find node[parentLevelId=0.0,levelId=1]
			 */
			String parentLevel = span.getParentLevel();
			int idx = parentLevel.lastIndexOf("\\.");
			if (idx < 0) {
				throw new BuildTraceSpanTreeException("parentLevel="
						+ parentLevel + " is unexpected.");
			}
			TraceSpanNode foundNode = findNodeAndCreateVisualNodeIfNess(
					parentLevel.substring(0, idx),
					Integer.parseInt(parentLevel.substring(idx + 1)));

		}
	}

	private TraceSpanNode findNodeAndCreateVisualNodeIfNess(
			String parentLevelId, int levelId) {
		String levelDesc = StringUtil.isBlank(parentLevelId) ? (levelId + "")
				: (parentLevelId + "." + levelId);
		String[] levelArray = levelDesc.split("\\.");

		TraceSpanNode currentNode = treeRoot;
		String contextParentLevelId = "";
		for (String currentLevel : levelArray) {
			int currentLevelInt = Integer.parseInt(currentLevel);
			for (int i = 0; i < currentLevelInt; i++) {
				if (currentNode.hasNext()) {
					currentNode = currentNode.next();
				} else {
					// create visual next node
					currentNode = new VisualTraceSpanNode(null, null,
							currentNode, null, contextParentLevelId, i);
				}
			}
			contextParentLevelId = contextParentLevelId == "" ? ("" + currentLevelInt)
					: (contextParentLevelId + "." + currentLevelInt);
			if (currentNode.hasSub()) {
				currentNode = currentNode.sub();
			} else {
				// create visual sub node
				currentNode = new VisualTraceSpanNode(currentNode, null, null,
						null, contextParentLevelId, 0);
			}
		}

		return currentNode;
	}

	private String generateChainToken(Span level0Span)
			throws BuildTraceSpanTreeException {
		if (StringUtil.isBlank(level0Span.getParentLevel())
				&& level0Span.getLevelId() == 0) {
			StringBuilder chainTokenDesc = new StringBuilder();
			chainTokenDesc.append(level0Span.getViewPointId());
			return TokenGenerator.generateCID(chainTokenDesc.toString());
		} else {
			throw new BuildTraceSpanTreeException("tid:"
					+ level0Span.getTraceId() + " level0 span data is illegal");
		}
	}

}