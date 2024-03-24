package com.norman.webviewup.lib.service.binder;

import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

import java.io.FileDescriptor;


public class ProxyBinder implements IBinder {

    private final IBinder mRemoteBinder;

    private final IInterface mStubIInterface;

    private IInterface mProxyIInterface;


    public ProxyBinder(IInterface stubIInterface) {
        this.mStubIInterface = stubIInterface;
        this.mRemoteBinder = stubIInterface.asBinder();
    }

    public ProxyBinder(IInterface stubIInterface,IInterface proxyIInterface) {
        this(stubIInterface);
        setProxyInterface(proxyIInterface);
    }

    public synchronized void setProxyInterface(IInterface proxyIInterface) {
        this.mProxyIInterface = proxyIInterface;
    }

    public synchronized IInterface getProxyIInterface() {
        return mProxyIInterface;
    }

    @Override
    public String getInterfaceDescriptor() throws RemoteException {
        return mRemoteBinder.getInterfaceDescriptor();
    }

    @Override
    public boolean pingBinder() {
        return mRemoteBinder.pingBinder();
    }

    @Override
    public boolean isBinderAlive() {
        return mRemoteBinder.isBinderAlive();
    }

    @Override
    public synchronized IInterface queryLocalInterface(String descriptor) {
        return mProxyIInterface != null? mProxyIInterface :mStubIInterface;
    }

    @Override
    public void dump(FileDescriptor fd, String[] args) throws RemoteException {
        mRemoteBinder.dump(fd, args);
    }

    @Override
    public void dumpAsync(FileDescriptor fd, String[] args) throws RemoteException {
        mRemoteBinder.dumpAsync(fd, args);
    }

    @Override
    public boolean transact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        return mRemoteBinder.transact(code, data, reply, flags);
    }

    @Override
    public void linkToDeath(DeathRecipient recipient, int flags) throws RemoteException {
        mRemoteBinder.linkToDeath(recipient, flags);
    }

    @Override
    public boolean unlinkToDeath(DeathRecipient recipient, int flags) {
        return mRemoteBinder.unlinkToDeath(recipient, flags);
    }
}

