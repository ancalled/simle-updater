<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="text" encoding="utf-8" indent="yes" />

    <xsl:template match="/builds">
        <xsl:value-of select="./build[1]/@number"/>
    </xsl:template>

</xsl:stylesheet>

