import { BrowserRouter } from "react-router-dom";
import OmniAgentLayout from "./components/OmniAgentLayout.tsx";
import { ChatSessionsProvider } from "./contexts/ChatSessionsContext.tsx";

function App() {
  return (
    <BrowserRouter>
      <ChatSessionsProvider>
        <OmniAgentLayout />
      </ChatSessionsProvider>
    </BrowserRouter>
  );
}

export default App;
