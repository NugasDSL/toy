package toy.proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ClientCalls.asyncClientStreamingCall;
import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;
import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.blockingServerStreamingCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ServerCalls.asyncClientStreamingCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.13.1)",
    comments = "Source: blockchainService.proto")
public final class BlockchainServiceGrpc {

  private BlockchainServiceGrpc() {}

  public static final String SERVICE_NAME = "proto.BlockchainService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<toy.proto.Types.Transaction,
      toy.proto.Types.accepted> getAddTransactionMethod;

  public static io.grpc.MethodDescriptor<toy.proto.Types.Transaction,
      toy.proto.Types.accepted> getAddTransactionMethod() {
    io.grpc.MethodDescriptor<toy.proto.Types.Transaction, toy.proto.Types.accepted> getAddTransactionMethod;
    if ((getAddTransactionMethod = BlockchainServiceGrpc.getAddTransactionMethod) == null) {
      synchronized (BlockchainServiceGrpc.class) {
        if ((getAddTransactionMethod = BlockchainServiceGrpc.getAddTransactionMethod) == null) {
          BlockchainServiceGrpc.getAddTransactionMethod = getAddTransactionMethod = 
              io.grpc.MethodDescriptor.<toy.proto.Types.Transaction, toy.proto.Types.accepted>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "proto.BlockchainService", "addTransaction"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  toy.proto.Types.Transaction.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  toy.proto.Types.accepted.getDefaultInstance()))
                  .setSchemaDescriptor(new BlockchainServiceMethodDescriptorSupplier("addTransaction"))
                  .build();
          }
        }
     }
     return getAddTransactionMethod;
  }

  private static volatile io.grpc.MethodDescriptor<toy.proto.Types.read,
      toy.proto.Types.approved> getGetTransactionMethod;

  public static io.grpc.MethodDescriptor<toy.proto.Types.read,
      toy.proto.Types.approved> getGetTransactionMethod() {
    io.grpc.MethodDescriptor<toy.proto.Types.read, toy.proto.Types.approved> getGetTransactionMethod;
    if ((getGetTransactionMethod = BlockchainServiceGrpc.getGetTransactionMethod) == null) {
      synchronized (BlockchainServiceGrpc.class) {
        if ((getGetTransactionMethod = BlockchainServiceGrpc.getGetTransactionMethod) == null) {
          BlockchainServiceGrpc.getGetTransactionMethod = getGetTransactionMethod = 
              io.grpc.MethodDescriptor.<toy.proto.Types.read, toy.proto.Types.approved>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "proto.BlockchainService", "getTransaction"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  toy.proto.Types.read.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  toy.proto.Types.approved.getDefaultInstance()))
                  .setSchemaDescriptor(new BlockchainServiceMethodDescriptorSupplier("getTransaction"))
                  .build();
          }
        }
     }
     return getGetTransactionMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static BlockchainServiceStub newStub(io.grpc.Channel channel) {
    return new BlockchainServiceStub(channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static BlockchainServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new BlockchainServiceBlockingStub(channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static BlockchainServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new BlockchainServiceFutureStub(channel);
  }

  /**
   */
  public static abstract class BlockchainServiceImplBase implements io.grpc.BindableService {

    /**
     */
    public void addTransaction(toy.proto.Types.Transaction request,
        io.grpc.stub.StreamObserver<toy.proto.Types.accepted> responseObserver) {
      asyncUnimplementedUnaryCall(getAddTransactionMethod(), responseObserver);
    }

    /**
     */
    public void getTransaction(toy.proto.Types.read request,
        io.grpc.stub.StreamObserver<toy.proto.Types.approved> responseObserver) {
      asyncUnimplementedUnaryCall(getGetTransactionMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getAddTransactionMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                toy.proto.Types.Transaction,
                toy.proto.Types.accepted>(
                  this, METHODID_ADD_TRANSACTION)))
          .addMethod(
            getGetTransactionMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                toy.proto.Types.read,
                toy.proto.Types.approved>(
                  this, METHODID_GET_TRANSACTION)))
          .build();
    }
  }

  /**
   */
  public static final class BlockchainServiceStub extends io.grpc.stub.AbstractStub<BlockchainServiceStub> {
    private BlockchainServiceStub(io.grpc.Channel channel) {
      super(channel);
    }

    private BlockchainServiceStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected BlockchainServiceStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new BlockchainServiceStub(channel, callOptions);
    }

    /**
     */
    public void addTransaction(toy.proto.Types.Transaction request,
        io.grpc.stub.StreamObserver<toy.proto.Types.accepted> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getAddTransactionMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void getTransaction(toy.proto.Types.read request,
        io.grpc.stub.StreamObserver<toy.proto.Types.approved> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getGetTransactionMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   */
  public static final class BlockchainServiceBlockingStub extends io.grpc.stub.AbstractStub<BlockchainServiceBlockingStub> {
    private BlockchainServiceBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }

    private BlockchainServiceBlockingStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected BlockchainServiceBlockingStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new BlockchainServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public toy.proto.Types.accepted addTransaction(toy.proto.Types.Transaction request) {
      return blockingUnaryCall(
          getChannel(), getAddTransactionMethod(), getCallOptions(), request);
    }

    /**
     */
    public toy.proto.Types.approved getTransaction(toy.proto.Types.read request) {
      return blockingUnaryCall(
          getChannel(), getGetTransactionMethod(), getCallOptions(), request);
    }
  }

  /**
   */
  public static final class BlockchainServiceFutureStub extends io.grpc.stub.AbstractStub<BlockchainServiceFutureStub> {
    private BlockchainServiceFutureStub(io.grpc.Channel channel) {
      super(channel);
    }

    private BlockchainServiceFutureStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected BlockchainServiceFutureStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new BlockchainServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<toy.proto.Types.accepted> addTransaction(
        toy.proto.Types.Transaction request) {
      return futureUnaryCall(
          getChannel().newCall(getAddTransactionMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<toy.proto.Types.approved> getTransaction(
        toy.proto.Types.read request) {
      return futureUnaryCall(
          getChannel().newCall(getGetTransactionMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_ADD_TRANSACTION = 0;
  private static final int METHODID_GET_TRANSACTION = 1;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final BlockchainServiceImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(BlockchainServiceImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_ADD_TRANSACTION:
          serviceImpl.addTransaction((toy.proto.Types.Transaction) request,
              (io.grpc.stub.StreamObserver<toy.proto.Types.accepted>) responseObserver);
          break;
        case METHODID_GET_TRANSACTION:
          serviceImpl.getTransaction((toy.proto.Types.read) request,
              (io.grpc.stub.StreamObserver<toy.proto.Types.approved>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  private static abstract class BlockchainServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    BlockchainServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return toy.proto.BlockchainServiceOuterClass.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("BlockchainService");
    }
  }

  private static final class BlockchainServiceFileDescriptorSupplier
      extends BlockchainServiceBaseDescriptorSupplier {
    BlockchainServiceFileDescriptorSupplier() {}
  }

  private static final class BlockchainServiceMethodDescriptorSupplier
      extends BlockchainServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    BlockchainServiceMethodDescriptorSupplier(String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (BlockchainServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new BlockchainServiceFileDescriptorSupplier())
              .addMethod(getAddTransactionMethod())
              .addMethod(getGetTransactionMethod())
              .build();
        }
      }
    }
    return result;
  }
}
