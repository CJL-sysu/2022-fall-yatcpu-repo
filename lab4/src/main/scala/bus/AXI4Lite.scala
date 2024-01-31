// Copyright 2021 Howard Lau
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package bus

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util._
import riscv.Parameters

object AXI4Lite {
  val protWidth = 3
  val respWidth = 2
}

class AXI4LiteWriteAddressChannel(addrWidth: Int) extends Bundle { // Address Write

  val AWVALID = Output(Bool())//主机写地址有效1
  val AWREADY = Input(Bool())   //从机写地址线空闲
  val AWADDR = Output(UInt(addrWidth.W))//主机写地址1
  val AWPROT = Output(UInt(AXI4Lite.protWidth.W))//

}

class AXI4LiteWriteDataChannel(dataWidth: Int) extends Bundle {  //(Data) Write
  val WVALID = Output(Bool())  //1
  val WREADY = Input(Bool())  //从机写数据线空闲
  val WDATA = Output(UInt(dataWidth.W))//1
  val WSTRB = Output(UInt((dataWidth / 8).W))
}

class AXI4LiteWriteResponseChannel extends Bundle { //B (Write Response)
  val BVALID = Input(Bool())   //从机写回复有效
  val BREADY = Output(Bool())  //1
  val BRESP = Input(UInt(AXI4Lite.respWidth.W))  //从机写回复内容
}

class AXI4LiteReadAddressChannel(addrWidth: Int) extends Bundle {  //Address Read
  val ARVALID = Output(Bool())   //主机读请求12
  val ARREADY = Input(Bool())    //从机读准备（从机读地址线空闲）
  val ARADDR = Output(UInt(addrWidth.W))//主机读地址12
  val ARPROT = Output(UInt(AXI4Lite.protWidth.W))//没用
}

class AXI4LiteReadDataChannel(dataWidth: Int) extends Bundle {  //(Data) Read
  val RVALID = Input(Bool())  //从机读返回请求（读数据有效）
  val RREADY = Output(Bool())//主机可以读取返回数据的状态读准备1
  val RDATA = Input(UInt(dataWidth.W))  //从机读数据输出
  val RRESP = Input(UInt(AXI4Lite.respWidth.W)) //从机读回复？没用
}

class AXI4LiteInterface(addrWidth: Int, dataWidth: Int) extends Bundle {
    val AWVALID = Output(Bool())
    val AWREADY = Input(Bool())
    val AWADDR = Output(UInt(addrWidth.W))
    val AWPROT = Output(UInt(AXI4Lite.protWidth.W))
    val WVALID = Output(Bool())
    val WREADY = Input(Bool())
    val WDATA = Output(UInt(dataWidth.W))
    val WSTRB = Output(UInt((dataWidth / 8).W))
    val BVALID = Input(Bool())
    val BREADY = Output(Bool())
    val BRESP = Input(UInt(AXI4Lite.respWidth.W))
    val ARVALID = Output(Bool())
    val ARREADY = Input(Bool())
    val ARADDR = Output(UInt(addrWidth.W))
    val ARPROT = Output(UInt(AXI4Lite.protWidth.W))
    val RVALID = Input(Bool())
    val RREADY = Output(Bool())
    val RDATA = Input(UInt(dataWidth.W))
    val RRESP = Input(UInt(AXI4Lite.respWidth.W))
}//上面几个class的所有成员

class AXI4LiteChannels(addrWidth: Int, dataWidth: Int) extends Bundle {//从机负责input，主机负责output
  val write_address_channel = new AXI4LiteWriteAddressChannel(addrWidth)//27行
  val write_data_channel = new AXI4LiteWriteDataChannel(dataWidth)//36行
  val write_response_channel = new AXI4LiteWriteResponseChannel()//43行
  val read_address_channel = new AXI4LiteReadAddressChannel(addrWidth)//49行
  val read_data_channel = new AXI4LiteReadDataChannel(dataWidth)//56行
}//上面几个class
//从机
class AXI4LiteSlaveBundle(addrWidth: Int, dataWidth: Int) extends Bundle {
  val read = Output(Bool())
  val write = Output(Bool())
  val read_data = Input(UInt(dataWidth.W))
  val read_valid = Input(Bool())
  val write_data = Output(UInt(dataWidth.W))
  val write_strobe = Output(Vec(Parameters.WordSize, Bool()))
  val address = Output(UInt(addrWidth.W))
}
//主机
class AXI4LiteMasterBundle(addrWidth: Int, dataWidth: Int) extends Bundle {
  val read = Input(Bool())
  val write = Input(Bool())
  val read_data = Output(UInt(dataWidth.W))//已赋值
  val write_data = Input(UInt(dataWidth.W))
  val write_strobe = Input(Vec(Parameters.WordSize, Bool()))
  val address = Input(UInt(addrWidth.W))

  val busy = Output(Bool())//已赋值
  val read_valid = Output(Bool())//已赋值
  val write_valid = Output(Bool())//已赋值
}

object AXI4LiteStates extends ChiselEnum {
  val Idle, ReadAddr, ReadData, WriteAddr, WriteData, WriteResp = Value
}//空闲       读地址     读数据       写地址     写数据       写回复
//从机
class AXI4LiteSlave(addrWidth: Int, dataWidth: Int) extends Module {
  val io = IO(new Bundle {
    val channels = Flipped(new AXI4LiteChannels(addrWidth, dataWidth))  //85行，负责input //用于主机和从机数据交换
    val bundle = new AXI4LiteSlaveBundle(addrWidth, dataWidth)  //93行,负责input//bundle连接从设备
  })
  //给AXI4LiteSlaveBundle（93行）所有input赋值
  val state = RegInit(AXI4LiteStates.Idle)
  val addr = RegInit(0.U(dataWidth.W))
  io.bundle.address := addr
  val read = RegInit(false.B)
  io.bundle.read := read
  val write = RegInit(false.B)
  io.bundle.write := write
  val write_data = RegInit(0.U(dataWidth.W))
  io.bundle.write_data := write_data
  val write_strobe = RegInit(VecInit(Seq.fill(Parameters.WordSize)(false.B)))
  io.bundle.write_strobe := write_strobe
  //给AXI4LiteChannels（85行）赋值（对应所有input）
  val ARREADY = RegInit(false.B)//51 从机读地址线空闲
  io.channels.read_address_channel.ARREADY := ARREADY
  val RVALID = RegInit(false.B)//57 从机读数据有效
  io.channels.read_data_channel.RVALID := RVALID
  val RRESP = RegInit(0.U(AXI4Lite.respWidth))//60 从机读回复
  io.channels.read_data_channel.RRESP := RRESP

  io.channels.read_data_channel.RDATA := io.bundle.read_data

  val AWREADY = RegInit(false.B)//30 从机写地址线空闲
  io.channels.write_address_channel.AWREADY := AWREADY
  val WREADY = RegInit(false.B)//38 从机写数据线空闲
  io.channels.write_data_channel.WREADY := WREADY
  write_data := io.channels.write_data_channel.WDATA
  val BVALID = RegInit(false.B)//44 从机写回复有效
  io.channels.write_response_channel.BVALID := BVALID
  val BRESP = WireInit(0.U(AXI4Lite.respWidth))//46 从机写回复内容
  io.channels.write_response_channel.BRESP := BRESP
  //lab4(BUS)
  when(state === AXI4LiteStates.Idle) { //空闲
    read := false.B
    write := false.B
    ARREADY := false.B
    RVALID := false.B
    AWREADY := false.B
    WREADY := false.B
    BVALID := false.B
    when(io.channels.read_address_channel.ARVALID) { //读地址有效
      state := AXI4LiteStates.ReadAddr
      addr := io.channels.read_address_channel.ARADDR
      //ARREADY := true.B //告诉主机读准备//改
    }.elsewhen(io.channels.write_address_channel.AWVALID) { //写地址有效
      state := AXI4LiteStates.WriteAddr
      //      AWREADY := true.B
    }
  }.elsewhen(state === AXI4LiteStates.ReadAddr) { //接收读地址
    read := true.B
    when(io.channels.read_address_channel.ARREADY) {
      state := AXI4LiteStates.ReadData
    }
    ARREADY := true.B
    when(io.channels.read_data_channel.RREADY) {
      ARREADY := false.B
    }
  }.elsewhen(state === AXI4LiteStates.WriteAddr) { //接收写地址
    addr := io.channels.write_address_channel.AWADDR
    AWREADY := true.B
    state := AXI4LiteStates.WriteData
  }.elsewhen(state === AXI4LiteStates.ReadData) { //发送读数据
    when(io.bundle.read_valid) {
      io.channels.read_data_channel.RDATA := io.bundle.read_data
      RVALID := true.B
      when(io.channels.read_data_channel.RREADY) {
        state := AXI4LiteStates.Idle
        ARREADY := false.B
      }
    }
  }.elsewhen(state === AXI4LiteStates.WriteData) { //接收写数据
    WREADY := true.B
    when(io.channels.write_data_channel.WVALID) {
      AWREADY := false.B
      write_data := io.channels.write_data_channel.WDATA
      write_strobe := io.channels.write_data_channel.WSTRB.asBools
      write := true.B
      state := AXI4LiteStates.WriteResp
    }
  }.elsewhen(state === AXI4LiteStates.WriteResp) { //发送写回复
    BVALID := true.B
    when(io.channels.write_response_channel.BREADY) {
      WREADY := false.B
      state := AXI4LiteStates.Idle
      write := false.B
    }
  }
  //lab4(BUS)
}
//主机
class AXI4LiteMaster(addrWidth: Int, dataWidth: Int) extends Module {
  val io = IO(new Bundle {
    val channels = new AXI4LiteChannels(addrWidth, dataWidth)//85行，负责output，同AXI4LiteSlave
    val bundle = new AXI4LiteMasterBundle(addrWidth, dataWidth)//103行,负责output
  })
  //给103(106~113)行所有output赋值
  val state = RegInit(AXI4LiteStates.Idle)
  io.bundle.busy := state =/= AXI4LiteStates.Idle//111

  val addr = RegInit(0.U(dataWidth.W))
  val read_valid = RegInit(false.B)
  io.bundle.read_valid := read_valid//112
  val write_valid = RegInit(false.B)
  io.bundle.write_valid := write_valid//113
  val write_data = RegInit(0.U(dataWidth.W))
  val write_strobe = RegInit(VecInit(Seq.fill(Parameters.WordSize)(false.B)))
  val read_data = RegInit(0.U(dataWidth.W))

  io.channels.read_address_channel.ARADDR := 0.U//52
  val ARVALID = RegInit(false.B)
  io.channels.read_address_channel.ARVALID := ARVALID//50
  io.channels.read_address_channel.ARPROT := 0.U//53
  val RREADY = RegInit(false.B)
  io.channels.read_data_channel.RREADY := RREADY//58

  io.bundle.read_data := io.channels.read_data_channel.RDATA//106
  val AWVALID = RegInit(false.B)
  io.channels.write_address_channel.AWADDR := 0.U//31
  io.channels.write_address_channel.AWVALID := AWVALID//29
  val WVALID = RegInit(false.B)
  io.channels.write_data_channel.WVALID := WVALID//37
  io.channels.write_data_channel.WDATA := write_data//39
  io.channels.write_address_channel.AWPROT := 0.U//32
  io.channels.write_data_channel.WSTRB := write_strobe.asUInt//40
  val BREADY = RegInit(false.B)
  io.channels.write_response_channel.BREADY := BREADY//45

  //lab4(BUS)
  io.channels.read_address_channel.ARADDR := addr
  io.channels.write_address_channel.AWADDR := addr
  when(state === AXI4LiteStates.Idle) { //空闲
    read_valid := false.B
    write_valid := false.B
    ARVALID := false.B
    RREADY := false.B
    AWVALID := false.B
    WVALID := false.B
    BREADY := false.B
    when(io.bundle.read) {
      state := AXI4LiteStates.ReadAddr
      addr := io.bundle.address
      io.channels.read_address_channel.ARADDR := io.bundle.address
    }.elsewhen(io.bundle.write) {
      state := AXI4LiteStates.WriteAddr
      write_strobe := io.bundle.write_strobe
      addr := io.bundle.address
      write_data := io.bundle.write_data
    }
  }.elsewhen(state === AXI4LiteStates.ReadAddr) { //发送读地址
    io.channels.read_address_channel.ARADDR := addr
    ARVALID := true.B
    when(io.channels.read_address_channel.ARREADY) {
      io.channels.read_address_channel.ARADDR := addr
      ARVALID := false.B
      state := AXI4LiteStates.ReadData
      //RREADY := true.B
    }
  }.elsewhen(state === AXI4LiteStates.ReadData) { //接收读数据
    when(io.channels.read_data_channel.RVALID) {
      RREADY := true.B
      read_data := io.channels.read_data_channel.RDATA
      io.bundle.read_data := io.channels.read_data_channel.RDATA
      read_valid := true.B
      state := AXI4LiteStates.Idle
    }
  }.elsewhen(state === AXI4LiteStates.WriteAddr) { //发送写地址
    io.channels.write_address_channel.AWADDR := addr
    AWVALID := true.B
    when(io.channels.write_address_channel.AWREADY) {
      AWVALID := false.B
      state := AXI4LiteStates.WriteData
    }
  }.elsewhen(state === AXI4LiteStates.WriteData) { //发送写数据
    io.channels.write_data_channel.WDATA := io.bundle.write_data
    WVALID := true.B
    when(io.channels.write_data_channel.WREADY) {
      state := AXI4LiteStates.WriteResp
    }
  }.elsewhen(state === AXI4LiteStates.WriteResp) { //接收写回复
    BREADY := true.B
    when(io.channels.write_response_channel.BVALID) {
      WVALID := false.B
      write_valid := true.B
      state := AXI4LiteStates.Idle
    }
  }
  //lab4(BUS)end
}
