<%@page session="false"%><%--
  Copyright 1997-2009 Day Management AG
  Barfuesserplatz 6, 4001 Basel, Switzerland
  All Rights Reserved.

  This software is the confidential and proprietary information of
  Day Management AG, ("Confidential Information"). You shall not
  disclose such Confidential Information and shall use it only in
  accordance with the terms of the license agreement you entered into
  with Day.

  ==============================================================================

  Agent component

  Displays information about a replication agent.

--%><%@page contentType="text/html"
            pageEncoding="utf-8"
            import="com.day.cq.replication.Agent,
                    com.day.cq.replication.AgentConfig,
                    com.day.cq.replication.AgentManager,
                    com.day.cq.replication.ReplicationQueue,
                    com.day.cq.widget.HtmlLibraryManager,
                    com.day.cq.i18n.I18n" %><%
%><%@include file="/libs/foundation/global.jsp"%><%
    I18n i18n = new I18n(slingRequest);
    String id = currentPage.getName();
    String title = properties.get("jcr:title", id);  // user generated content, no i18n

    AgentManager agentMgr = sling.getService(AgentManager.class);
    Agent agent = agentMgr.getAgents().get(id);
    AgentConfig cfg = agent == null ? null : agent.getConfiguration();

    if (cfg == null || !cfg.getConfigPath().equals(currentNode.getPath())) {
        // agent not active
        agent = null;
    }

    // get icons
    String globalIcnCls = "cq-agent-header";
    String statusIcnCls = "cq-agent-status";
    if (agent == null) {
        statusIcnCls += "-inactive";
        globalIcnCls += "-off";
    } else {
        try {
            agent.checkValid();
            if (agent.isEnabled()) {
                globalIcnCls += "-on";
                statusIcnCls += "-ok";
            } else {
                globalIcnCls += "-off";
                statusIcnCls += "-disabled";
            }
        } catch (IllegalArgumentException e) {
            globalIcnCls += "-off";
            statusIcnCls += "-invalid";
        }
    }

%><!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN">
<html>
<head>
    <title><%= i18n.get("AEM Replication") %> | <%= xssAPI.encodeForHTML(title) %></title>
    <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
    <%
    HtmlLibraryManager htmlMgr = sling.getService(HtmlLibraryManager.class);
    if (htmlMgr != null) {
        htmlMgr.writeIncludes(slingRequest, out, "cq.wcm.edit", "cq.replication");
    }

    %>
    <script src="/libs/cq/ui/resources/cq-ui.js" type="text/javascript"></script>
</head>
<body>
    <h2 class="<%= globalIcnCls %>"><%= xssAPI.encodeForHTML(title) %> (<%= xssAPI.encodeForHTML(id) %>)</h2>
    <%
        String description = properties.get("jcr:description", "");  // user generated content, no i18n
            %><p><%= xssAPI.encodeForHTML(description) %></p><%
    %><div id="agent-details"><cq:include path="<%= resource.getPath() + ".details.html" %>" resourceType="<%= resource.getResourceType() %>"/></div>
    <div>
    <br>
    <%
        // draw the 'edit' bar explicitly. since we want to be able to edit the
        // settings on publish too. we are too late here for setting the WCMMode.
        /*
        out.flush();
        if (editContext != null) {
            editContext.getEditConfig().getToolbar().add(0, new Toolbar.Label("Settings"));
            editContext.includeEpilog(slingRequest, slingResponse, WCMMode.EDIT);
        }
        */

    %>
        <script type="text/javascript">
        CQ.WCM.edit({
            "path":"<%= resource.getPath() %>",
            "dialog":"/apps/akamai/replication/components/akamai/dialog",
            "type":"akamai/replication/components/akamai",
            "editConfig":{
                "xtype":"editbar",
                "listeners":{
                    "afteredit":"REFRESH_PAGE"
                },
                "inlineEditing":CQ.wcm.EditBase.INLINE_MODE_NEVER,
                "disableTargeting": true,
                "actions":[
                    {
                        "xtype":"tbtext",
                        "text":"Settings"
                    },
                    CQ.wcm.EditBase.EDIT
                ]
            }
        });
        </script>
    </div>

    <%
        if (agent != null) {
    %>
    <div id="CQ">
        <div id="cq-queue">
        </div>
    </div>

    <script type="text/javascript">
        function reloadDetails() {
            var url = CQ.HTTP.externalize("<%= currentPage.getPath() %>.details.html");
            var response = CQ.HTTP.get(url);
            if (CQ.HTTP.isOk(response)) {
                document.getElementById("agent-details").innerHTML = response.responseText;
            }
        }

        CQ.Ext.onReady(function(){
            var queue = new CQ.wcm.ReplicationQueue({
                url: "<%= currentPage.getPath() %>/jcr:content.queue.json",
                applyTo: "cq-queue",
                height: 400
            });
            queue.on("afterrefresh", function(queue) {
                reloadDetails();
            });
            queue.on("aftercleared", function(queue) {
                reloadDetails();
            });
            queue.on("afterretry", function(queue) {
                reloadDetails();
            });
            queue.loadAgent("<%= id %>");
        });

        function test() {
            CQ.shared.Util.open(CQ.HTTP.externalize('<%= currentPage.getPath() %>.test.html'));
        }
    </script>
    <%
        } // if (agent != null)
    %>
</body>
</html>
