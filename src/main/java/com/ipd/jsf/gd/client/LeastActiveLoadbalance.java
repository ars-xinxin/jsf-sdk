/**
 * Copyright 2004-2048 .
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ipd.jsf.gd.client;

import java.util.List;
import java.util.Random;

import com.ipd.jsf.gd.msg.Invocation;
import com.ipd.jsf.gd.registry.Provider;
import com.ipd.jsf.gd.util.RpcStatus;

/**
 * Title: 最少活跃调用数<br>
 *
 * Description: 最少活跃调用数，相同活跃数的随机，活跃数指调用前后计数差，使慢的机器收到更少请求
 * <br>不支持权重，在容量规划时，不能通过权重把压力导向一台机器压测容量<br>
 */
public class LeastActiveLoadbalance extends Loadbalance {

    private final Random random = new Random();

    /**
     * @see Loadbalance#doSelect(Invocation, java.util.List)
     */
    public Provider doSelect(Invocation invocation, List<Provider> providers) {
        String interfaceId = invocation.getClazzName();
        String methodName = invocation.getMethodName();
        Provider selectedProvider = null;

        int length = providers.size(); // 总个数
        int leastActive = -1; // 最小的活跃数
        int leastCount = 0; // 相同最小活跃数的个数
        int[] leastIndexs = new int[length]; // 相同最小活跃数的下标
        int totalWeight = 0; // 总权重
        int firstWeight = 0; // 第一个权重，用于于计算是否相同
        boolean sameWeight = true; // 是否所有权重相同
        for (int i = 0; i < length; i++) {
            Provider provider = providers.get(i);
            int active = RpcStatus.getStatus(interfaceId, methodName, provider).randomActive(); // 活跃数(按照最近100次调用，概率返回虚假超大并发数）
            int weight = getWeight(provider); // 权重
            if (leastActive == -1 || active < leastActive) { // 发现更小的活跃数，重新开始
                leastActive = active; // 记录最小活跃数
                leastCount = 1; // 重新统计相同最小活跃数的个数
                leastIndexs[0] = i; // 重新记录最小活跃数下标
                totalWeight = weight; // 重新累计总权重
                firstWeight = weight; // 记录第一个权重
                sameWeight = true; // 还原权重相同标识
            } else if (active == leastActive) { // 累计相同最小的活跃数
                leastIndexs[leastCount++] = i; // 累计相同最小活跃数下标
                totalWeight += weight; // 累计总权重
                // 判断所有权重是否一样
                if (sameWeight && i > 0
                        && weight != firstWeight) {
                    sameWeight = false;
                }
            }
        }
        if (leastCount == 1) {
            // 如果只有一个最小则直接返回
            selectedProvider = providers.get(leastIndexs[0]);
        } else if (!sameWeight && totalWeight > 0) {
            // 如果权重不相同且权重大于0则按总权重数随机
            int offsetWeight = random.nextInt(totalWeight);
            // 并确定随机值落在哪个片断上
            for (int i = 0; i < leastCount; i++) {
                int leastIndex = leastIndexs[i];
                offsetWeight -= getWeight(providers.get(leastIndex));
                if (offsetWeight <= 0) {
                    selectedProvider = providers.get(leastIndex);
                    break;
                }
            }
        } else {
            // 如果权重相同或权重为0则均等随机
            selectedProvider = providers.get(leastIndexs[random.nextInt(leastCount)]);
        }
        // 如果权重相同或权重为0则均等随机
        return selectedProvider;
    }

}