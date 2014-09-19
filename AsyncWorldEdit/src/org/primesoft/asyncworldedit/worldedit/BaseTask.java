/*
 * The MIT License
 *
 * Copyright 2014 SBPrime.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.primesoft.asyncworldedit.worldedit;

import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.history.change.BlockChange;
import com.sk89q.worldedit.history.change.Change;
import com.sk89q.worldedit.history.changeset.ChangeSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitRunnable;
import org.primesoft.asyncworldedit.ConfigProvider;
import org.primesoft.asyncworldedit.AsyncWorldEditMain;
import org.primesoft.asyncworldedit.blockPlacer.BlockPlacer;
import org.primesoft.asyncworldedit.blockPlacer.entries.JobEntry;
import org.primesoft.asyncworldedit.utils.SessionCanceled;
import org.primesoft.asyncworldedit.worldedit.history.InjectedArrayListHistory;

/**
 *
 * @author SBPrime
 */
public abstract class BaseTask extends BukkitRunnable {

    /**
     * Command name
     */
    protected final String m_command;

    /**
     * The player
     */
    protected final UUID m_player;

    /**
     * Cancelable edit session
     */
    protected final CancelabeEditSession m_cancelableEditSession;

    /**
     * Thread safe edit session
     */
    protected final ThreadSafeEditSession m_safeEditSession;
    

    /**
     * The edit session
     */
    protected final EditSession m_editSession;

    /**
     * The blocks placer
     */
    protected final BlockPlacer m_blockPlacer;

    /**
     * Job instance
     */
    protected final JobEntry m_job;

    public BaseTask(final EditSession editSession, final UUID player,
            final String commandName, BlockPlacer blocksPlacer, JobEntry job) {

        m_editSession = editSession;
        m_cancelableEditSession = (editSession instanceof CancelabeEditSession) ? (CancelabeEditSession) editSession : null;
        
        m_player = player;
        m_command = commandName;
        m_blockPlacer = blocksPlacer;
        m_job = job;

        if (m_cancelableEditSession != null) {
            m_safeEditSession = m_cancelableEditSession.getParent();
        } else {
            m_safeEditSession = (editSession instanceof ThreadSafeEditSession) ? (ThreadSafeEditSession) editSession : null;
        }
        
        if (m_safeEditSession != null) {
            m_safeEditSession.addAsync(job);
        }
    }

    @Override
    public void run() {
        Object result = null;
        try {
            m_job.setStatus(JobEntry.JobStatus.Preparing);
            if (ConfigProvider.isTalkative()) {
                AsyncWorldEditMain.say(m_player, ChatColor.LIGHT_PURPLE + "Running " + ChatColor.WHITE
                        + m_command + ChatColor.LIGHT_PURPLE + " in full async mode.");
            }
            m_blockPlacer.addTasks(m_player, m_job);
            if (m_cancelableEditSession == null || !m_cancelableEditSession.isCanceled()) {
                result = doRun();
            }

            if (m_editSession != null) {
                if (m_editSession.isQueueEnabled()) {
                    m_editSession.flushQueue();
                } else if (m_cancelableEditSession != null) {
                    m_cancelableEditSession.resetAsync();
                } else if (m_safeEditSession != null) {
                    m_safeEditSession.resetAsync();
                }
            }

            m_job.setStatus(JobEntry.JobStatus.Waiting);
            m_blockPlacer.addTasks(m_player, m_job);
            doPostRun(result);
        } catch (MaxChangedBlocksException ex) {
            AsyncWorldEditMain.say(m_player, ChatColor.RED + "Maximum block change limit.");
        } catch (IllegalArgumentException ex) {
            if (ex.getCause() instanceof SessionCanceled) {
                AsyncWorldEditMain.say(m_player, ChatColor.LIGHT_PURPLE + "Job canceled.");
            }
        }
        postProcess();

        m_job.taskDone();
        if (m_cancelableEditSession != null) {
            ThreadSafeEditSession parent = m_cancelableEditSession.getParent();
            parent.removeAsync(m_job);
        } else if (m_safeEditSession != null) {
            m_safeEditSession.removeAsync(m_job);
        }        
    }

    protected abstract Object doRun() throws MaxChangedBlocksException;

    protected abstract void doPostRun(Object result);
    
    protected void postProcess() {}
}