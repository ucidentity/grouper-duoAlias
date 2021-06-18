package edu.berkeley.calnet.grouper.duoAlias;

import org.apache.commons.jexl3.*;
import org.apache.commons.jexl3.JxltEngine;

/**
 *
 */
public class GrouperDuoAliasSet {

    /**
     *
     */
    public GrouperDuoAliasUtils() {
    }

    /**
     * alias name
     */
    private String aliasName;

    /**
     * alias expression
     */
    private JxltEngine.Expression expression;

    /**
     * alias value
     */
    private String aliasValue;


    /**
     * alias name
     * @return the alias name
     */
    public String getAliasName() {
        return this.aliasName;
    }


    /**
     * alias name
     * @param aliasName to set
     */
    public void setAliasName(String aliasName) {
        this.aliasName = aliasName;
    }


    /**
     * alias expression
     * @return the alias expression
     */
    public JxltEngine.Expression getExpression() {
        return this.expression;
    }


    /**
     * alias name
     * @param aliasName to set
     */
    public void setExpression(JxltEngine.Expression expression) {
        this.expression = expression;
    }

    /**
     * alias value
     * @return the alias value
     */
    public String getAliasValue() {
        return this.aliasValue;
    }


    /**
     * alias value
     * @param aliasValue to set
     */
    public void setAliasValue(String aliasValue) {
        this.aliasValue = aliasValue;
    }



}