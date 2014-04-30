<xsl:stylesheet
	version="2.0"
	xmlns="http://www.w3.org/1999/xhtml"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<xsl:include href="I.xsl"/>
<xsl:include href="E.xsl"/>
<xsl:include href="O.xsl"/>

<xsl:output omit-xml-declaration="yes"/>

<xsl:template match="/">
	<xsl:for-each select="results/TElist/TE">
		<xsl:variable name="teid" select="@id"/>
		<xsl:variable name="eid" select="@Eid"/>
		<xsl:variable name="filename" select="concat('path_', $teid, '.html')"/>
		<xsl:result-document href="{$filename}">
			<td style="width:50%">
			<xsl:for-each select="path">
				<table class="details">
					<colgroup>
						<col width="35%"/>
						<col width="65%"/>
					</colgroup>
					<tr>
						<td class="head4" colspan="2">
							Path
							<xsl:choose>
								<xsl:when test="truncated">(truncated)</xsl:when>
								<xsl:otherwise>(complete)</xsl:otherwise>
							</xsl:choose>
						</td>
					</tr>
					<tr>
						<td class="head4">Enclosing Lock(s)</td>
						<td class="head4">Call Site(s)</td>
					</tr>
					<xsl:text disable-output-escaping = "yes">&lt;tr&gt;&lt;td&gt;</xsl:text>
					<xsl:for-each select="elem|lock">
						<xsl:choose>
							<xsl:when test="name()='elem'">
								<xsl:text disable-output-escaping="yes">&lt;/td&gt;&lt;td&gt;</xsl:text>
								<xsl:variable name="Ielem" select="id(@Iid)"/>
								<xsl:apply-templates select="$Ielem"/> 
								<xsl:text disable-output-escaping="yes">&lt;/td&gt;&lt;/tr&gt;&lt;tr&gt;&lt;td&gt;</xsl:text>
							</xsl:when>
							<xsl:otherwise>
                            	<xsl:variable name="Lelem" select="id(@Lid)"/>
                           		<xsl:variable name="Melem" select="id(@Mid)"/>
                           		<xsl:variable name="file" select="$Lelem/@file"/>
                           		<xsl:variable name="line" select="$Lelem/@line"/>
                           		<a href="{$file}.html#{$line}">
                               		<xsl:value-of select="$Melem/@sign"/>
                           		</a><br/>
								<xsl:apply-templates select="id(@Oid)"/> <br/>
							</xsl:otherwise>
						</xsl:choose>
					</xsl:for-each>
					<xsl:for-each select="../lock">
						<xsl:variable name="Lelem" select="id(@Lid)"/>
                        <xsl:variable name="Melem" select="id(@Mid)"/>
                        <xsl:variable name="file" select="$Lelem/@file"/>
                        <xsl:variable name="line" select="$Lelem/@line"/>
                        <a href="{$file}.html#{$line}">
                          	<xsl:value-of select="$Melem/@sign"/>
						</a><br/>
						<xsl:apply-templates select="id(@Oid)"/> <br/>
					</xsl:for-each>
					<xsl:text disable-output-escaping="yes">&lt;/td&gt;&lt;td&gt;</xsl:text>
						<xsl:apply-templates select="id($eid)"/>
					<xsl:text disable-output-escaping="yes">&lt;/td&gt;&lt;/tr&gt;</xsl:text>
				</table>
			</xsl:for-each>
			</td>
		</xsl:result-document>
	</xsl:for-each>
</xsl:template>

</xsl:stylesheet>

