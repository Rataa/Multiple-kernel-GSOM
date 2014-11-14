/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.gsom.kernel;

/**
 *
 * @author maheshakya
 */
import com.gsom.core.NodeGrowthHandler;
import com.gsom.enums.InitType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.gsom.objects.GNode;
import com.gsom.util.GSOMConstants;
import com.gsom.util.Utils;

import com.gsom.kernel.GaussianKernelL2;
import com.gsom.kernel.LinearKernel;
import com.gsom.ui.MainWindow;
import java.util.Arrays;

public class MultipleKernelTrainer {
    
    private int numberOfKernels;
    private double[] coefficients;

    public double[] getCoefficients() {
        return coefficients;
    }
    
    private Map<String, GNode> nodeMap;
    private NodeGrowthHandler growthHandler;
    private InitType initType;

    public MultipleKernelTrainer(InitType initType) {
        this.initType = initType;
        nodeMap = new HashMap<String, GNode>();
        growthHandler = new NodeGrowthHandler();
        
        numberOfKernels = 2;
    }

    public Map<String,GNode> trainNetwork(ArrayList<String> iStrings, ArrayList<double[]> iWeights1, ArrayList<double[]> iWeights2) {
//        for(int i=0; i<iWeights.size();i++)
//            iWeights.set(i, Utils.normalizeVectorMinMax(iWeights.get(i)));
        
        initFourNodes(initType);	//init the map with four nodes  
        coefficients = initCoeffs();
         for (int i = 0; i < GSOMConstants.MAX_ITERATIONS; i++) {
            int k = 0;
            double learningRate = Utils.getLearningRate(i, nodeMap.size());
            double radius = Utils.getRadius(i, Utils.getTimeConst());
            for (int j=0; j<iWeights1.size(); j++)
            {
               trainForSingleIterAndSingleInput(i,iWeights1.get(j), iWeights2.get(j), iStrings.get(k), learningRate, radius);
                k++; 
            }
         }
        return nodeMap;
    }
    
    
    private void trainForSingleIterAndSingleInput(int iter, double[] input1, double[] input2, String str, double learningRate, double radius) {

        GNode winner = Utils.selectWinner(nodeMap, input1, input2, coefficients);

        winner.calcAndUpdateErr(input1, input2, coefficients);

        for (Map.Entry<String, GNode> entry : nodeMap.entrySet()) {
            entry.setValue(Utils.adjustNeighbourWeight(entry.getValue(), winner, input1, input2, radius, learningRate));
        }
        
        double[] influences  = new double[nodeMap.size()];
        int k = 0;
        for (Map.Entry<String, GNode> entry : nodeMap.entrySet()) {
            influences[k] = Utils.getInfluence(entry.getValue(), winner, radius, learningRate);
            k++;
                  
        }
        
        updateKernelCoefficients(learningRate, influences, input1, input2);
        

        if (winner.getErrorValue() > GSOMConstants.getGT()) {
            //System.out.println("Winner "+winner.getX()+","+winner.getY()+" GT exceeded");
            adjustWinnerError(winner);
        }
    }
    
    private void updateKernelCoefficients(double timeConstant, double[] influences, double[] inputs1, double[] inputs2){
       
        coefficients[0] += timeConstant*calcGradient(influences, inputs1, 0);
        coefficients[1] += timeConstant*calcGradient(influences, inputs2, inputs1.length);
    }
    
    private double calcGradient(double[] influences, double[] inputs, int coveredLength){
        double gradient = 0;
        
        int k = 0;
        for (Map.Entry<String, GNode> entry : nodeMap.entrySet()) {
             gradient += influences[k]*LinearKernel.LinearKernelDistance(inputs, Arrays.copyOfRange(entry.getValue().getWeights(), coveredLength, coveredLength+inputs.length), inputs.length);
             k++;
        }

        return gradient;
    }
    
    private double[] initCoeffs(){
        double[] arr = new double[this.numberOfKernels];
        double sum = 0;
        for (int i = 0; i < this.numberOfKernels; i++) {
            arr[i] = Math.random();
            sum += arr[i];
        }

        for (int i = 0; i < this.numberOfKernels; i++)
            arr[i] /= sum;

        return arr;
    }

    

    //Initialization of the map. 
    //this will create 4 nodes with random weights
    private void initFourNodes(InitType type) {
        if (type == InitType.RANDOM) {
            for (int i = 0; i < 2; i++) {
                for (int j = 0; j < 2; j++) {
                    GNode initNode = new GNode(i, j, Utils.generateRandomArray(GSOMConstants.DIMENSIONS));
                    nodeMap.put(Utils.generateIndexString(i, j), initNode);
                }
            }
        } else if (type == InitType.LINEAR) {
            double initVal = 0.1;
            for (int i = 0; i < 2; i++) {
                for (int j = 0; j < 2; j++) {
                    GNode initNode = new GNode(i, j, Utils.generateLinearArray(GSOMConstants.DIMENSIONS,initVal));
                    nodeMap.put(Utils.generateIndexString(i, j), initNode);
                    initVal += 0.1;
                }
            }
        }
    }

    //when a neuron wins its error value needs to be adjusted
    private void adjustWinnerError(GNode winner) {

        //on x-axis
        String nodeLeftStr = Utils.generateIndexString(winner.getX() - 1, winner.getY());
        String nodeRightStr = Utils.generateIndexString(winner.getX() + 1, winner.getY());

        //on y-axis
        String nodeTopStr = Utils.generateIndexString(winner.getX(), winner.getY() + 1);
        String nodeBottomStr = Utils.generateIndexString(winner.getX(), winner.getY());

        if (nodeMap.containsKey(nodeLeftStr)
                && nodeMap.containsKey(nodeRightStr)
                && nodeMap.containsKey(nodeTopStr)
                && nodeMap.containsKey(nodeBottomStr)) {
            distrErrToNeighbors(winner, nodeLeftStr, nodeRightStr, nodeTopStr, nodeBottomStr);
        } else {
            growthHandler.growNodes(nodeMap, winner); //NodeGrowthHandler takes over
        }
    }


    //distributing error to the neighbors of thw winning node
    private void distrErrToNeighbors(GNode winner, String leftK, String rightK, String topK, String bottomK) {
        winner.setErrorValue(GSOMConstants.getGT() / 2);
        nodeMap.get(leftK).setErrorValue(calcErrForNeighbour(nodeMap.get(leftK)));
        nodeMap.get(rightK).setErrorValue(calcErrForNeighbour(nodeMap.get(rightK)));
        nodeMap.get(topK).setErrorValue(calcErrForNeighbour(nodeMap.get(topK)));
        nodeMap.get(bottomK).setErrorValue(calcErrForNeighbour(nodeMap.get(bottomK)));
    }

    //error calculating equation for neighbours of a winner
    private double calcErrForNeighbour(GNode node) {
        return node.getErrorValue() + (GSOMConstants.FD * node.getErrorValue());
    }
}

