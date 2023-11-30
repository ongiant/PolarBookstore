package com.polarbookshop.dispatcherservice;

import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class HiddenIterator {
    private final Set<Integer> set = Collections.synchronizedSet(new HashSet<Integer>());

    public void add(Integer i) {
        set.add(i);
    }

    public void remove(Integer i) {
        set.remove(i);
    }

    public void addTenThings() {
        Random r = new Random();
        for (int i = 0; i < 1000000; i++)
            add(r.nextInt());
        System.out.println("DEBUG: added ten elements to " + set);
    }

    public static void main(String[] args) throws InterruptedException {
        HiddenIterator hi = new HiddenIterator();
        Thread t1 = new Thread(new Runnable(){
            @Override
            public void run(){
                hi.addTenThings();
                try {
                    TimeUnit.MILLISECONDS.sleep(5);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        Thread t2 = new Thread(new Runnable(){
            @Override
            public void run(){
                hi.addTenThings();
            }
        });

        t1.start();

        t2.start();
    }
}
