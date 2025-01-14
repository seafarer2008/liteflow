package com.yomahub.liteflow.flow.element.condition;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.yomahub.liteflow.core.proxy.ComponentProxy;
import com.yomahub.liteflow.enums.ConditionTypeEnum;
import com.yomahub.liteflow.enums.NodeTypeEnum;
import com.yomahub.liteflow.exception.NoSwitchTargetNodeException;
import com.yomahub.liteflow.exception.SwitchTargetCannotBePreOrFinallyException;
import com.yomahub.liteflow.exception.SwitchTypeErrorException;
import com.yomahub.liteflow.flow.element.Executable;
import com.yomahub.liteflow.flow.element.Node;
import com.yomahub.liteflow.slot.DataBus;
import com.yomahub.liteflow.slot.Slot;
import com.yomahub.liteflow.util.LiteFlowProxyUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * 选择Condition
 * @author Bryan.Zhang
 * @since 2.8.0
 */
public class SwitchCondition extends Condition{

    private final Map<String, Executable> targetMap = new HashMap<>();

    private final String TAG_PREFIX = "tag:";

    @Override
    public void execute(Integer slotIndex) throws Exception {
        if (ListUtil.toList(NodeTypeEnum.SWITCH, NodeTypeEnum.SWITCH_SCRIPT).contains(this.getSwitchNode().getType())){
            //先执行switch节点
            this.getSwitchNode().setCurrChainName(this.getCurrChainName());
            this.getSwitchNode().execute(slotIndex);

            //根据switch节点执行出来的结果选择
            Slot slot = DataBus.getSlot(slotIndex);
            //这里可能会有spring代理过的bean，所以拿到user原始的class
            Class<?> originalClass = LiteFlowProxyUtil.getUserClass(this.getSwitchNode().getInstance().getClass());
            String targetId = slot.getSwitchResult(originalClass.getName());
            if (StrUtil.isNotBlank(targetId)) {
                Executable targetExecutor;

                //这里要判断是否跳转到tag
                if (targetId.startsWith(TAG_PREFIX)){
                    String targetTag = targetId.replaceAll(TAG_PREFIX, "");
                    targetExecutor = targetMap.values().stream().filter(executable -> {
                        if (executable instanceof Node){
                            Node node = (Node) executable;
                            return targetTag.equals(node.getTag());
                        }else{
                            return false;
                        }
                    }).findFirst().orElse(null);
                }else{
                    targetExecutor = targetMap.get(targetId);
                }

                if (ObjectUtil.isNotNull(targetExecutor)) {
                    //switch的目标不能是Pre节点或者Finally节点
                    if (targetExecutor instanceof PreCondition || targetExecutor instanceof FinallyCondition){
                        String errorInfo = StrUtil.format("[{}]:switch component[{}] error, switch target node cannot be pre or finally", slot.getRequestId(), this.getSwitchNode().getInstance().getDisplayName());
                        throw new SwitchTargetCannotBePreOrFinallyException(errorInfo);
                    }
                    targetExecutor.setCurrChainName(this.getCurrChainName());
                    targetExecutor.execute(slotIndex);
                }else{
                    String errorInfo = StrUtil.format("[{}]:no target node find for the component[{}]", slot.getRequestId(), this.getSwitchNode().getInstance().getDisplayName());
                    throw new NoSwitchTargetNodeException(errorInfo);
                }
            }
        }else{
            throw new SwitchTypeErrorException("switch instance must be NodeSwitchComponent");
        }
    }

    @Override
    public ConditionTypeEnum getConditionType() {
        return ConditionTypeEnum.TYPE_SWITCH;
    }

    public void addTargetItem(Executable executable){
        this.targetMap.put(executable.getExecuteName(), executable);
    }

    public void setSwitchNode(Node switchNode) {
        this.getExecutableList().add(switchNode);
    }

    public Map<String, Executable> getTargetMap() {
        return targetMap;
    }

    public Node getSwitchNode(){
        return (Node) this.getExecutableList().get(0);
    }
}
