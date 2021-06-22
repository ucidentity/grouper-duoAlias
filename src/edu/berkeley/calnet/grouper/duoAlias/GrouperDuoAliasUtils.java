/**
 * @author jeffmc
 * $Id$
 */
package edu.berkeley.calnet.grouper.duoAlias;

import java.util.Set;

import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;
import edu.internet2.middleware.subject.Subject;
import edu.internet2.middleware.grouper.util.GrouperUtil;


import java.util.Map;
import java.util.Arrays;

import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import org.apache.commons.jexl3.*;
import org.apache.commons.jexl3.JxltEngine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class GrouperDuoAliasUtils {
  static Map<String, GrouperDuoAliasSet> groupNameAliasMap = new HashMap();
  private static Logger LOG = LoggerFactory.getLogger(GrouperDuoAliasUtils.class);


  public GrouperDuoAliasUtils() {
  }


  /**
   * check group name
   * @param groupName
   * @return boolean (true if the groupName is one of ours)
   **/
    public static Boolean validGroupName(String groupName){
      LOG.debug("The groupName to check: " + groupName);
      return groupNameAliasMap.containsKey(groupName);
    }


  /**
   * get group names to check
   * @return Set (group names)
   **/
  public static Set getGroupNames(){
    for(String groupName : groupNameAliasMap.keySet()) {
      LOG.debug("GroupName to check: " + groupName);
    }
    return groupNameAliasMap.keySet();
  }

  /**
   * createJxltExpression
   * @param InputExpression
   * @return JxltEngine.Expression
   **/
    private static JxltEngine.Expression createJxltExpression(String inputExpression){
      JxltEngine jxlt = new JexlBuilder().create().createJxltEngine();
      JxltEngine.Expression exp = jxlt.createExpression( inputExpression );
      return exp;
    }

  /**
   * getAliasValue
   * @param subject
   * @param groupName
   * @return GrouperDuoAliasSet
   **/
    public static GrouperDuoAliasSet getAliasValue(Subject subject, String groupName){
      GrouperDuoAliasSet aliasSet = groupNameAliasMap.get(groupName);
      JxltEngine.Expression exp = aliasSet.getExpression();
      JexlContext context = new MapContext();
      context.set("subject", subject);
      // work it out
      String aliasValue = (String) exp.evaluate(context);
      aliasSet.setAliasValue(aliasValue);
      return aliasSet;
    }


  /**
   * doAliasSetup
   * read the alias properties and create groupNameAlias Map
   * @return None
   **/
    public static void doAliasSetup() {
      List<String> aliasList = Arrays.asList("alias1", "alias2", "alias3", "alias4", "alias5", "alias6", "alias7", "alias8");

      String aliases = GrouperLoaderConfig.retrieveConfig().propertyValueStringRequired("grouperDuoAlias.aliases");
      String[] aliasArray = aliases.split("[, ]+");
      for (String alias : aliasArray) {
        alias = alias.toLowerCase();
        if (aliasList.contains(alias)) {
          if ((GrouperLoaderConfig.retrieveConfig().propertyValueStringRequired(
                  String.format("changeLog.consumer.duoAlias.%s.groupName", alias)) != null)
                  && (GrouperLoaderConfig.retrieveConfig().propertyValueStringRequired(
                  String.format("changeLog.consumer.duoAlias.%s.expression", alias)) != null)) {
            JxltEngine.Expression exp = createJxltExpression(GrouperLoaderConfig.retrieveConfig().propertyValueStringRequired(
                    String.format("grouperDuoAlias.%s.expression", alias)));
            GrouperDuoAliasSet aliasSet = new GrouperDuoAliasSet();
            aliasSet.setAliasName(alias);
            aliasSet.setExpression(exp);
            aliasSet.setAliasValue("");
            groupNameAliasMap.put(GrouperLoaderConfig.retrieveConfig().propertyValueStringRequired(
                    String.format("grouperDuoAlias.%s.groupName", alias)), aliasSet);
          } else {
            LOG.debug("no attributes avaialbe for " + alias);
          }
        } else {
          LOG.debug(String.format("The alias isn't correct. It is %s", alias));
        }
      }

      LOG.debug("Here are the groupName/alias/expressions we found:");
      for (String groupName : groupNameAliasMap.keySet()) {
        LOG.debug(String.format("GroupName: %s, Alias: %s, Expression %s",
                groupName, groupNameAliasMap.get(groupName).getAliasName(), groupNameAliasMap.get(groupName).getExpression()));
      }
    }

      /**
       * subject attribute to get the duo username from the subject, could be "id" for subject id
       * @return the subject attribute name
       */
      public static String configSubjectAttributeForDuoUsername() {
        return GrouperLoaderConfig.retrieveConfig().propertyValueStringRequired("grouperDuoAlias.subjectAttributeForDuoUsername");
      }


      /**
       * sources for subjects
       * @return the config sources for subjects
       */
      public static Set<String> configSourcesForSubjects() {

        //# put the comma separated list of sources to send to duo
        //grouperDuo.sourcesForSubjects = someSource
        String sourcesForSubjectsString = GrouperLoaderConfig.retrieveConfig().propertyValueStringRequired("grouperDuoAlias.sourcesForSubjects");

        return GrouperUtil.splitTrimToSet(sourcesForSubjectsString, ",");
      }


    }
