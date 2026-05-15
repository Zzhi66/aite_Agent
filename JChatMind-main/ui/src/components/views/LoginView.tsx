import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Form, Input, Tabs, message } from "antd";
import { LockOutlined, UserOutlined } from "@ant-design/icons";
import { useAuth } from "../../contexts/AuthContext.tsx";

type TabKey = "login" | "register";

const LoginView: React.FC = () => {
  const navigate = useNavigate();
  const { login, register } = useAuth();
  const [activeTab, setActiveTab] = useState<TabKey>("login");
  const [submitting, setSubmitting] = useState(false);
  const [loginForm] = Form.useForm();
  const [registerForm] = Form.useForm();

  /** 登录成功后跳转主页 */
  const handleLogin = async (values: {
    username: string;
    password: string;
  }) => {
    setSubmitting(true);
    try {
      await login(values);
      message.success("登录成功");
      navigate("/", { replace: true });
    } catch (err) {
      const msg = err instanceof Error ? err.message : "登录失败";
      message.error(msg);
    } finally {
      setSubmitting(false);
    }
  };

  /** 注册成功后切换到登录 Tab */
  const handleRegister = async (values: {
    username: string;
    password: string;
    nickname?: string;
  }) => {
    setSubmitting(true);
    try {
      await register(values);
      message.success("注册成功，请登录");
      setActiveTab("login");
      loginForm.setFieldsValue({ username: values.username });
      registerForm.resetFields();
    } catch (err) {
      const msg = err instanceof Error ? err.message : "注册失败";
      message.error(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const tabItems = [
    {
      key: "login",
      label: "登录",
      children: (
        <Form
          form={loginForm}
          layout="vertical"
          onFinish={handleLogin}
          autoComplete="off"
        >
          <Form.Item
            name="username"
            label="用户名"
            rules={[
              { required: true, message: "请输入用户名" },
              { min: 3, message: "用户名至少 3 个字符" },
            ]}
          >
            <Input
              prefix={<UserOutlined />}
              placeholder="请输入用户名"
              size="large"
            />
          </Form.Item>
          <Form.Item
            name="password"
            label="密码"
            rules={[
              { required: true, message: "请输入密码" },
              { min: 6, message: "密码至少 6 个字符" },
            ]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="请输入密码"
              size="large"
            />
          </Form.Item>
          <Form.Item className="mb-0">
            <Button
              type="primary"
              htmlType="submit"
              block
              size="large"
              loading={submitting}
            >
              登录
            </Button>
          </Form.Item>
        </Form>
      ),
    },
    {
      key: "register",
      label: "注册",
      children: (
        <Form
          form={registerForm}
          layout="vertical"
          onFinish={handleRegister}
          autoComplete="off"
        >
          <Form.Item
            name="username"
            label="用户名"
            rules={[
              { required: true, message: "请输入用户名" },
              { min: 3, max: 50, message: "用户名长度为 3-50 个字符" },
            ]}
          >
            <Input
              prefix={<UserOutlined />}
              placeholder="请输入用户名"
              size="large"
            />
          </Form.Item>
          <Form.Item name="nickname" label="昵称（可选）">
            <Input placeholder="显示名称" size="large" />
          </Form.Item>
          <Form.Item
            name="password"
            label="密码"
            rules={[
              { required: true, message: "请输入密码" },
              { min: 6, message: "密码至少 6 个字符" },
            ]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="请输入密码"
              size="large"
            />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            label="确认密码"
            dependencies={["password"]}
            rules={[
              { required: true, message: "请再次输入密码" },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue("password") === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error("两次输入的密码不一致"));
                },
              }),
            ]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="请再次输入密码"
              size="large"
            />
          </Form.Item>
          <Form.Item className="mb-0">
            <Button
              type="primary"
              htmlType="submit"
              block
              size="large"
              loading={submitting}
            >
              注册
            </Button>
          </Form.Item>
        </Form>
      ),
    },
  ];

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-50 via-blue-50 to-indigo-100 p-4">
      <div className="w-full max-w-md bg-white rounded-2xl shadow-xl p-8">
        <div className="text-center mb-8">
          <h1 className="text-2xl font-bold text-gray-900">Aite助手</h1>
        </div>
        <Tabs
          activeKey={activeTab}
          onChange={(key) => setActiveTab(key as TabKey)}
          items={tabItems}
          centered
        />
      </div>
    </div>
  );
};

export default LoginView;
