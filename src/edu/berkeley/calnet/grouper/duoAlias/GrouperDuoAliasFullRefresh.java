/**
 * @author jeffmc
 * $Id$
 */
package edu.berkeley.calnet.grouper.duoAlias;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Date;

import edu.berkeley.calnet.grouper.duoAlias.GrouperDuoAliasLog;
import edu.berkeley.calnet.grouper.duoAlias.GrouperDuoAliasUtils;
import edu.berkeley.calnet.grouper.duoAlias.GrouperDuoAliasCommands;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.quartz.DisallowConcurrentExecution;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.GrouperSession;
import edu.internet2.middleware.grouper.Member;
import edu.internet2.middleware.grouper.Stem;
import edu.internet2.middleware.grouper.Stem.Scope;
import edu.internet2.middleware.grouper.StemFinder;
import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;
import edu.internet2.middleware.grouper.app.loader.GrouperLoaderScheduleType;
import edu.internet2.middleware.grouper.app.loader.GrouperLoaderStatus;
import edu.internet2.middleware.grouper.app.loader.GrouperLoaderType;
import edu.internet2.middleware.grouper.app.loader.OtherJobBase;
import edu.internet2.middleware.grouper.pit.PITGroup;
import edu.internet2.middleware.grouper.app.loader.db.Hib3GrouperLoaderLog;
import edu.internet2.middleware.grouper.util.GrouperUtil;
import edu.internet2.middleware.subject.Subject;
import edu.internet2.middleware.subject.SubjectNotFoundException;


/**
 *
 */
@DisallowConcurrentExecution
public class GrouperDuoAliasFullRefresh extends OtherJobBase {

  /**
   * 
   * @param args
   */
  public static void main(String[] args) {
    fullRefreshLogic();
  }
  
  /**
   * change log temp to change log
   */
  public static final String GROUPER_DUO_FULL_REFRESH = "CHANGE_LOG_grouperDuoFullRefresh";

  /** logger */
  private static final Log LOG = GrouperUtil.getLog(GrouperDuoAliasFullRefresh.class);

  /**
   * 
   */
  public GrouperDuoAliasFullRefresh() {
  }

  /**
   * 
   */
  public static void fullRefreshLogic() {
    OtherJobInput otherJobInput = new OtherJobInput();
    GrouperSession grouperSession = GrouperSession.startRootSession();
    otherJobInput.setGrouperSession(grouperSession);
    Hib3GrouperLoaderLog hib3GrouploaderLog = new Hib3GrouperLoaderLog();
    otherJobInput.setHib3GrouperLoaderLog(hib3GrouploaderLog);
    try {
      fullRefreshLogic(otherJobInput);
    } finally {
      GrouperSession.stopQuietly(grouperSession);
    }
  }
  
  /**
   * full refresh logic
   * @param otherJobInput 
   */
  public static void fullRefreshLogic(OtherJobInput otherJobInput) {
    GrouperSession grouperSession = otherJobInput.getGrouperSession();
    
    Map<String, Object> debugMap = new LinkedHashMap<String, Object>();

    long startTimeNanos = System.nanoTime();

    debugMap.put("method", "fullRefreshLogic");

    //lets enter a log entry so it shows up as error in the db
    Hib3GrouperLoaderLog hib3GrouploaderLog = otherJobInput.getHib3GrouperLoaderLog();
    hib3GrouploaderLog.setHost(GrouperUtil.hostname());
    hib3GrouploaderLog.setJobName(GrouperDuoAliasFullRefresh.GROUPER_DUO_FULL_REFRESH);
    hib3GrouploaderLog.setJobScheduleType(GrouperLoaderScheduleType.CRON.name());
    hib3GrouploaderLog.setJobType(GrouperLoaderType.MAINTENANCE.name());

    hib3GrouploaderLog.setStartedTime(new Timestamp(System.currentTimeMillis()));
    
    long startedMillis = System.currentTimeMillis();
    String groupName = "";
    GrouperDuoAliasSet aliasSet = null;
    
    try {
      
      //# do setup, then grab the listed groups
      // next get the group
      GrouperDuoAliasUtils.doAliasSetup();
      Set<String> groupNames = GrouperDuoAliasUtils.getGroupNames();
      Map<String, Group> groupNameToGroupMap = new HashMap<String, Group>();
      for (groupName : groupNames){
        groupNameToGroupMap.put(groupName, findByName(grouperSession, groupName, true));
      }

       debugMap.put("grouperGroupNameCount", groupNameToGroupMap.size());
      

      debugMap.put("millisGetData", System.currentTimeMillis() - startedMillis);
      hib3GrouploaderLog.setMillisGetData((int)(System.currentTimeMillis() - startedMillis));
      long startedUpdateData = System.currentTimeMillis();


      //# put the comma separated list of sources to send to duo
      //grouperDuo.sourcesForSubjects = pennperson
      Set<String> sourcesForSubjects = GrouperDuoAliasUtils.configSourcesForSubjects();
      
      //# either have id for subject id or an attribute for the duo username (e.g. netId)
      //grouperDuo.subjectAttributeForDuoUsername = pennname
      String subjectAttributeForDuoUsername = GrouperDuoAliasUtils.configSubjectAttributeForDuoUsername();

      int insertCount = 0;
      int deleteCount = 0;
      int unresolvableCount = 0;
      Subject subject = null;
      String grouperUsername = "";
      
      //loop through groups
      for (groupName : groupNameToGroupMap.keySet()) {
        
        Group grouperGroup = groupNameToGroupMap.get(groupName);


        Set<String> grouperUsernamesInGroup = new HashSet<String>();

        //get usernames from grouper
        for (Member member : grouperGroup.getMembers()) {

          if (sourcesForSubjects.contains(member.getSubjectSourceId())) {
            if (StringUtils.equals("id", subjectAttributeForDuoUsername)) {
              grouperUsernamesInGroup.add(member.getSubjectId());
            } else {
              try {
                Subject subject = member.getSubject();
                String attributeValue = subject.getAttributeValue(subjectAttributeForDuoUsername);
                if (StringUtils.isBlank(attributeValue)) {
                  //i guess this is ok
                  LOG.info("Subject has a blank: " + subjectAttributeForDuoUsername + ", " + member.getSubjectSourceId() + ", " + member.getSubjectId());
                  unresolvableCount++;
                } else {
                  grouperUsernamesInGroup.add(attributeValue);
                }
              } catch (SubjectNotFoundException snfe) {
                unresolvableCount++;
                LOG.error("Cant find subject: " + member.getSubjectSourceId() + ": " +  member.getSubjectId());
                //i guess continue
              }
            }
          }
        }

        debugMap.put("grouperSubjectCount_" + grouperGroup.getExtension(), grouperUsernamesInGroup.size());
        totalCount += grouperUsernamesInGroup.size();


        //add aliases to duo users
        for (grouperUsername : grouperUsernamesInGroup) {
          String duoUserId = GrouperDuoAliasCommands.retrieveUserIdFromUsername(grouperUsername);
          if (StringUtils.isBlank(duoUserId)) {
            LOG.warn("User is not in duo: " + grouperUsername);
          } else {
            insertCount++;
            aliasSet = GrouperDuoAliasUtils.getAliasValue(subject, groupName);
            GrouperDuoAliasCommands.assignDuoUserAlias(duoUserId, aliasSet.getAliasName(), aliasSet.getAliasValue());
          }
        }


        Set<Member> membersThatHaveBeenDeleted = grouperGroup.getMembers("id", grouperGroup.getCreateTime(),
                                                                          new java.util.Date(), sourcesForSubjects, null);
        Set<Member> currentMembers = grouperGroup.getMembers();

        membersThatHaveBeenDeleted.removeAll(currentMembers);
        Set<String> grouperUsernamesToDeleteAlias = new HashSet<String>();

        //get usernames from grouper
        for (Member member : membersThatHaveBeenDeleted) {

          if (sourcesForSubjects.contains(member.getSubjectSourceId())) {
            if (StringUtils.equals("id", subjectAttributeForDuoUsername)) {
              grouperUsernamesToDeleteAlias.add(member.getSubjectId());
            } else {
              try {
                Subject subject = member.getSubject();
                String attributeValue = subject.getAttributeValue(subjectAttributeForDuoUsername);
                if (StringUtils.isBlank(attributeValue)) {
                  //i guess this is ok
                  LOG.info("Subject has a blank: " + subjectAttributeForDuoUsername + ", " + member.getSubjectSourceId() + ", " + member.getSubjectId());
                  unresolvableCount++;
                } else {
                  grouperUsernamesToDeleteAlias.add(attributeValue);
                }
              } catch (SubjectNotFoundException snfe) {
                unresolvableCount++;
                LOG.error("Cant find subject: " + member.getSubjectSourceId() + ": " +  member.getSubjectId());
                //i guess continue
              }
            }
          }
        }

        //remove aliases for duo users that have been deleted
        for (grouperUsername : grouperUsernamesInGroup) {
          String duoUserId = GrouperDuoAliasCommands.retrieveUserIdFromUsername(grouperUsername);
          if (StringUtils.isBlank(duoUserId)) {
            LOG.warn("User is not in duo: " + grouperUsername);
          } else {
            deleteCount++;
            aliasSet = GrouperDuoAliasUtils.getAliasValue(subject, groupName);
            GrouperDuoAliasCommands.assignDuoUserAlias(duoUserId, aliasSet.getAliasName(), "");
          }
        }
        debugMap.put("removes_" + grouperGroup.getExtension(), duoUsernamesNotInGrouper.size());

        debugMap.put("grouperSubjectCount_" + groupName, insertCount);
        totalCount += insertCount;


      }
      debugMap.put("millisLoadData", System.currentTimeMillis() - startedUpdateData);
      hib3GrouploaderLog.setMillisLoadData((int)(System.currentTimeMillis() - startedUpdateData));
      debugMap.put("millis", System.currentTimeMillis() - startedMillis);
      hib3GrouploaderLog.setEndedTime(new Timestamp(System.currentTimeMillis()));
      hib3GrouploaderLog.setMillis((int)(System.currentTimeMillis() - startedMillis));
      
      //lets enter a log entry so it shows up as error in the db
      hib3GrouploaderLog.setJobMessage(GrouperUtil.mapToString(debugMap));
      hib3GrouploaderLog.setStatus(GrouperLoaderStatus.SUCCESS.name());
      hib3GrouploaderLog.setUnresolvableSubjectCount(unresolvableCount);
      hib3GrouploaderLog.setInsertCount(insertCount);
      hib3GrouploaderLog.setDeleteCount(deleteCount);
      hib3GrouploaderLog.setTotalCount(totalCount);
      hib3GrouploaderLog.store();
      
    } catch (Exception e) {
      debugMap.put("exception", ExceptionUtils.getFullStackTrace(e));
      String errorMessage = "Problem running job: '" + GrouperLoaderType.GROUPER_CHANGE_LOG_TEMP_TO_CHANGE_LOG + "'";
      LOG.error(errorMessage, e);
      errorMessage += "\n" + ExceptionUtils.getFullStackTrace(e);
      try {
        //lets enter a log entry so it shows up as error in the db
        hib3GrouploaderLog.setMillis((int)(System.currentTimeMillis() - startedMillis));
        hib3GrouploaderLog.setEndedTime(new Timestamp(System.currentTimeMillis()));
        hib3GrouploaderLog.setJobMessage(errorMessage);
        hib3GrouploaderLog.setStatus(GrouperLoaderStatus.CONFIG_ERROR.name());
        hib3GrouploaderLog.store();
        
      } catch (Exception e2) {
        LOG.error("Problem logging to loader db log", e2);
      }
    
    } finally {
      GrouperDuoAliasLog.duoLog(debugMap, startTimeNanos);
    }
  }

  /**
   * @see edu.internet2.middleware.grouper.app.loader.OtherJobBase#run(edu.internet2.middleware.grouper.app.loader.OtherJobBase.OtherJobInput)
   */
  @Override
  public OtherJobOutput run(OtherJobInput otherJobInput) {
    OtherJobOutput otherJobOutput = new OtherJobOutput();
    fullRefreshLogic(otherJobInput);

    return otherJobOutput;
  }

}
