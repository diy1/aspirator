<xsl:stylesheet
	version="2.0"
	xmlns="http://www.w3.org/1999/xhtml"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:chord="http://chord.stanford.edu/">

<xsl:include href="A.xsl"/>
<xsl:include href="O.xsl"/>

<xsl:function name="chord:printThrd">
    <xsl:param name="Telem"/>
	<td>Thread:
		<xsl:apply-templates select="$Telem"/> <br/>
	</td>
</xsl:function>

<xsl:function name="chord:printLock">
    <xsl:param name="Lelem"/>
    <xsl:param name="Oelem"/>
	<xsl:variable name="file" select="$Lelem/@file"/>
	<xsl:variable name="line" select="$Lelem/@line"/>
	<td>Lock held at <a href="{$file}.html#{$line}">
		<xsl:value-of select="$file"/>:<xsl:value-of select="$line"/></a> <br/>
		allocated at <xsl:apply-templates select="$Oelem"/>
	</td>
</xsl:function>

<xsl:template match="/">
	<xsl:result-document href="deadlocks.html">
	<html>
	<head>
		<title>Deadlocks</title>
   		<link rel="stylesheet" href="style.css" type="text/css"/>
	</head>
	<body>
	<table class="summary">
	<tr>
		<td class="head1">Deadlock Reports</td>
	</tr>
	<xsl:for-each-group select="results/deadlocklist/deadlock" group-by="@group">
		<tr>
			<td class="head4">Group <xsl:value-of select="position()"/></td>
		</tr>
    <xsl:for-each select="current-group()">
		<tr>
			<td class="head3">Report <xsl:value-of select="position()"/></td>
		</tr>
		<tr><td><table border="0" width="100%">
		<colgroup>
			<col width="50%"/>
			<col width="50%"/>
		</colgroup>
		<xsl:variable name="T1elem" select="id(@T1id)"/>
		<xsl:variable name="T2elem" select="id(@T2id)"/>
		<tr>
			<xsl:copy-of select="chord:printThrd($T1elem)"/>
			<xsl:copy-of select="chord:printThrd($T2elem)"/>
		</tr>
		<xsl:variable name="M1elem" select="id(@M1id)"/>
		<xsl:variable name="M2elem" select="id(@M2id)"/>
		<xsl:variable name="M3elem" select="id(@M3id)"/>
		<xsl:variable name="M4elem" select="id(@M4id)"/>
		<xsl:variable name="O1elem" select="id(@O1id)"/>
		<xsl:variable name="O2elem" select="id(@O2id)"/>
		<xsl:variable name="O3elem" select="id(@O3id)"/>
		<xsl:variable name="O4elem" select="id(@O4id)"/>
		<xsl:variable name="L1elem" select="id(@L1id)"/>
		<xsl:variable name="L2elem" select="id(@L2id)"/>
		<xsl:variable name="L3elem" select="id(@L3id)"/>
		<xsl:variable name="L4elem" select="id(@L4id)"/>
		<tr>
			<xsl:copy-of select="chord:printLock($L1elem, $O1elem)"/>
			<xsl:copy-of select="chord:printLock($L3elem, $O3elem)"/>
		</tr>
		<tr>
			<xsl:copy-of select="chord:printLock($L2elem, $O2elem)"/>
			<xsl:copy-of select="chord:printLock($L4elem, $O4elem)"/>
		</tr>
		<tr>
			<td>
				<xsl:variable name="T1Mid" select="id(@T1id)/@Mid"/>
				<xsl:variable name="M1id" select="@M1id"/>
				<xsl:variable name="M2id" select="@M2id"/>
				[<a href="path_{$T1Mid}_{$M1id}.html">Shortest path from thread root to first lock</a>]
				<xsl:if test="$M1id != $M2id">
					[<a href="path_{$M1id}_{$M2id}.html">Shortest path from first lock to second lock</a>]
				</xsl:if>
			</td>
			<td>
				<xsl:variable name="T2Mid" select="id(@T2id)/@Mid"/>
				<xsl:variable name="M3id" select="@M3id"/>
				<xsl:variable name="M4id" select="@M4id"/>
				[<a href="path_{$T2Mid}_{$M3id}.html">Shortest path from thread root to first lock</a>]
				<xsl:if test="$M3id != $M4id">
					[<a href="path_{$M3id}_{$M4id}.html">Shortest path from first lock to second lock</a>]
				</xsl:if>
			</td>
		</tr>
		</table></td></tr>
	</xsl:for-each>
	</xsl:for-each-group>
	</table>
	</body>
	</html>
	</xsl:result-document>
</xsl:template>

</xsl:stylesheet>

