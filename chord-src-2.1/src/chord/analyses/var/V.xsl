<xsl:stylesheet
	version="2.0"
	xmlns="http://www.w3.org/1999/xhtml"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<xsl:import href="M.xsl"/>

<xsl:template match="V">
	<xsl:variable name="file" select="@file"/>
    <xsl:variable name="method" select="id(@Mid)"/>
    <xsl:variable name="varName" select="@name"/>
    
    <xsl:for-each select="tokenize(@line, ',')">
        <xsl:variable name="line" select="."/>
    	&lt;<xsl:apply-templates select="$method"/>,
    	<a href="{$file}.html#{$line}">
			<xsl:value-of select="$varName"/>
    		&gt;
    	</a>
	</xsl:for-each>
</xsl:template>

</xsl:stylesheet>

