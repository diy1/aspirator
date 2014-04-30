<xsl:stylesheet
    version="2.0"
    xmlns="http://www.w3.org/1999/xhtml"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:chord="http://chord.stanford.edu/">

<xsl:function name="chord:sort_targets">
    <xsl:param name="targets"/>
    <xsl:param name="java_analysis_path"/>
    <xsl:param name="dlog_analysis_path"/>
    <xsl:param name="groupingAttrName"/>
	<xsl:param name="desc"/>
    <html>
    <head>
      <title><xsl:value-of select="$desc"/></title>
      <link rel="stylesheet" href="style.css" type="text/css"/>
    </head>
    <body>
	<p><font size="+1"><b>Java analysis path: </b> <tt><xsl:value-of select="$java_analysis_path"/></tt></font></p>
	<p><font size="+1"><b>Dlog analysis path: </b> <tt><xsl:value-of select="$dlog_analysis_path"/></tt></font></p>
    <table class="summary">
    <tr>
      <td class="head4center"><a href="targets_sortby_name.html"><b>Target Name</b></a></td>
	  <td class="head4center"><a href="targets_sortby_kind.html"><b>Target Kind</b></a></td>
	  <td class="head4center"><a href="targets_sortby_producers.html"><b>Target Producer(s)</b></a></td>
    </tr>
	<xsl:for-each select="$targets">
	  <xsl:sort select="@*[name() = $groupingAttrName]"/>
      <tr>
	    <td class="head3"><xsl:value-of select="@name"/></td>
	    <td class="head3"><xsl:value-of select="@kind"/></td>
	    <xsl:variable name="producer_url" select="@producer_url"/>
	    <td class="head3"><a href="{$producer_url}"><xsl:value-of select="@producer_name"/></a>
          <xsl:for-each select="producer">
            <xsl:variable name="producer_url" select="@producer_url"/>
            <xsl:text>, </xsl:text><a href="{$producer_url}"><xsl:value-of select="@producer_name"/></a>
          </xsl:for-each>
        </td>
      </tr>
    </xsl:for-each>
    </table>
    </body>
    </html>
</xsl:function>

<xsl:template match="/">
	<xsl:variable name="targets" select="targets/target"/>
	<xsl:variable name="java_analysis_path" select="targets/@java_analysis_path"/>
	<xsl:variable name="dlog_analysis_path" select="targets/@dlog_analysis_path"/>
	<xsl:result-document href="targets_sortby_name.html">
        <xsl:copy-of select="chord:sort_targets($targets, $java_analysis_path, $dlog_analysis_path,
			'name', 'Targets (Sorted by Name)')"/>
    </xsl:result-document>
	<xsl:result-document href="targets_sortby_kind.html">
        <xsl:copy-of select="chord:sort_targets($targets, $java_analysis_path, $dlog_analysis_path,
			'kind', 'Targets (Sorted by Name)')"/>
    </xsl:result-document>
	<xsl:result-document href="targets_sortby_producers.html">
        <xsl:copy-of select="chord:sort_targets($targets, $java_analysis_path, $dlog_analysis_path,
			'producer_name', 'Targets (Sorted by Name)')"/>
    </xsl:result-document>
</xsl:template>

</xsl:stylesheet>

